import SwiftUI
import Combine

// MARK: - Internal State Management
// These remain private/internal as they are implementation details of the View.

private enum PresentmentState {
    case prepareSettings
    case showQrCode
    case transacting
    case completed
}

@MainActor
final class MdocPresentmentViewModel: ObservableObject {
    @Published fileprivate var state: PresentmentState = .prepareSettings
    @Published var qrCodeToShow: String?
    @Published var transactionError: Error?
    
    private var transactionTask: Task<Void, Never>?
    private var currentTransactionId: Foundation.UUID?
    
    // Dependencies
    let source: PresentmentSource
    let eDeviceKeyCurve: EcCurve
    let transportFactory: MdocTransportFactory
    
    init(source: PresentmentSource, eDeviceKeyCurve: EcCurve, transportFactory: MdocTransportFactory) {
        self.source = source
        self.eDeviceKeyCurve = eDeviceKeyCurve
        self.transportFactory = transportFactory
    }
    
    func startTransaction(qrSettings: MdocProximityQrSettings) {
        transactionTask?.cancel()
        let transactionId = Foundation.UUID()
        currentTransactionId = transactionId
        self.transactionError = nil
        transactionTask = Task {
            var transactionFailure: Error?
            var connectionSession: MdocConnectionSession?
            defer { connectionSession?.close() }
            do {
                let eDeviceKey = try await Crypto.shared.createEcPrivateKey(curve: eDeviceKeyCurve)
                
                let advertisedTransports = try await ConnectionHelperKt.advertise(
                    qrSettings.availableConnectionMethods,
                    role: MdocRole.mdoc,
                    transportFactory: transportFactory,
                    options: qrSettings.createTransportOptions
                )

                let deviceEngagement = buildDeviceEngagement(
                    eDeviceKey: eDeviceKey.publicKey,
                    version: "1.0"
                ) { builder in
                    advertisedTransports.forEach {
                        builder.addConnectionMethod(connectionMethod: $0.connectionMethod)
                    }
                }

                let encodedDeviceEngagement = try Cbor.shared.encode(item: deviceEngagement.toDataItem())
                if !Task.isCancelled && self.currentTransactionId == transactionId {
                    self.qrCodeToShow = "mdoc:" + encodedDeviceEngagement.toBase64Url()
                    self.state = .showQrCode
                }
                
                let session = try MdocConnectionSession(
                    transports: advertisedTransports,
                    eSenderKey: eDeviceKey.publicKey
                )
                connectionSession = session
                let transport = try await session.waitForConnection()

                if !Task.isCancelled && self.currentTransactionId == transactionId {
                    self.state = .transacting
                }
                
                try await Iso18013Presentment(
                    transport: transport,
                    eDeviceKey: eDeviceKey,
                    deviceEngagement: deviceEngagement.toDataItem(),
                    handover: Simple.companion.NULL,
                    source: source,
                    keyAgreementPossible: [eDeviceKeyCurve],
                    preselectedDocuments: [],
                    insertSequenceNumbers: false,
                    timeout: KotlinDurationCompanion.shared.fromSeconds(seconds: 15),
                    timeoutSubsequentRequests: KotlinDurationCompanion.shared.fromSeconds(seconds: 30),
                    onWaitingForRequest: onWaitingForRequest,
                    onWaitingForUserInput: onWaitingForUserInput,
                    onDocumentsInFocus: onDocumenstInFocus,
                    onSendingResponse: onSendingResponse
                )
                
            } catch {
                transactionFailure = error
            }
            
            if let session = connectionSession {
                session.close()
                // This bounded cleanup task does not inherit the transaction task's cancellation.
                // Its SKIE dispatcher remains live until awaitClosed returns.
                let cleanup = await Task { try await session.awaitClosed() }.result
                if case .failure(let error) = cleanup {
                    if let primaryError = transactionFailure {
                        transactionFailure = NSError(
                            domain: "MdocPresentmentCleanup", code: 0,
                            userInfo: [
                                NSUnderlyingErrorKey: primaryError,
                                NSLocalizedDescriptionKey: "\(primaryError.localizedDescription); \(error.localizedDescription)"
                            ]
                        )
                    } else {
                        transactionFailure = error
                    }
                }
            }

            if !Task.isCancelled && self.currentTransactionId == transactionId {
                self.transactionError = transactionFailure
                self.state = .completed
                self.transactionTask = nil
                self.currentTransactionId = nil
            }
        }
    }
    
    func cancelTransaction() {
        currentTransactionId = nil
        transactionTask?.cancel()
        transactionTask = nil
    }
    
    func reset() {
        self.state = .prepareSettings
    }
}

private func onWaitingForRequest() -> Void {}
private func onWaitingForUserInput() -> Void {}
private func onDocumenstInFocus(_ documents: [Multipaz.Document]) -> Void {}
private func onSendingResponse() -> Void {}

// MARK: - Public SwiftUI View

/// A SwiftUI view for presentment with QR engagement according to ISO/IEC 18013-5:2021.
public struct MdocProximityQrPresentment<
    PrepareSettingsContent: View,
    ShowQrCodeContent: View,
    ShowTransactingContent: View,
    ShowCompletedContent: View
>: View {
    
    // MARK: Public Properties
    public let preselectedDocuments: [Multipaz.Document]
    public let eDeviceKeyCurve: EcCurve
    public let transportFactory: MdocTransportFactory
    
    // MARK: View Builders
    @ViewBuilder public let prepareSettings: (_ generateQrCode: @escaping (MdocProximityQrSettings) -> Void) -> PrepareSettingsContent
    @ViewBuilder public let showQrCode: (_ uri: String, _ reset: @escaping () -> Void) -> ShowQrCodeContent
    @ViewBuilder public let showTransacting: (_ reset: @escaping () -> Void) -> ShowTransactingContent
    @ViewBuilder public let showCompleted: (_ error: Error?, _ reset: @escaping () -> Void) -> ShowCompletedContent
    
    // MARK: Internal State
    @StateObject private var viewModel: MdocPresentmentViewModel

    /// Initializer for ``MdocProximityQrPresentment``.
    ///
    /// - Parameters:
    ///   - source: a ``PresentmentSource`` which contains the source of truth of what to present.
    ///   - preselectedDocuments: a list of documents the user may have preselected or the empty list.
    ///   - eDeviceKeyCurve: the curve to use for session encryption.
    ///   - transportFactory: the ``MdocTransportFactory`` to use for creating transports.
    ///   - prepareSettings: a ``View`` which can be used to start the sharing process, for example a button the user can press that says "Present mDL with QR code", if applicable. This should call the passed in ``generateQrCode`` closure when e.g. the user presses the button and pass a ``MdocProximityQrSettings`` which contains the settings for what kind of ``Transport`` instances to advertise and what options to use when creating the transports.
    ///   - showQrCode: a ``View`` which shows the QR code and asks the user to scan it.
    ///   - showTransacting: a ``View`` which will be shown when transacting with a remote reader.
    ///   - showCompleted: a ``View`` which will be shown when the transaction is complete. This should should show feedback (either success or error, depending on the error parameter) and call the passed-in ``reset()`` closure when ready to reset the state and go back and show a QR button.
    public init(
        source: PresentmentSource,
        preselectedDocuments: [Multipaz.Document] = [],
        eDeviceKeyCurve: EcCurve = .p256,
        transportFactory: MdocTransportFactory = MdocTransportFactoryDefault(),
        @ViewBuilder prepareSettings: @escaping (_ generateQrCode: @escaping (MdocProximityQrSettings) -> Void) -> PrepareSettingsContent,
        @ViewBuilder showQrCode: @escaping (_ uri: String, _ reset: @escaping () -> Void) -> ShowQrCodeContent,
        @ViewBuilder showTransacting: @escaping (_ reset: @escaping () -> Void) -> ShowTransactingContent,
        @ViewBuilder showCompleted: @escaping (_ error: Error?, _ reset: @escaping () -> Void) -> ShowCompletedContent
    ) {
        self.preselectedDocuments = preselectedDocuments
        self.eDeviceKeyCurve = eDeviceKeyCurve
        self.transportFactory = transportFactory
        self.prepareSettings = prepareSettings
        self.showQrCode = showQrCode
        self.showTransacting = showTransacting
        self.showCompleted = showCompleted
        
        _viewModel = StateObject(wrappedValue: MdocPresentmentViewModel(
            source: source,
            eDeviceKeyCurve: eDeviceKeyCurve,
            transportFactory: transportFactory
        ))
    }
    
    // MARK: Body
    public var body: some View {
        VStack {
            switch viewModel.state {
            case .prepareSettings:
                prepareSettings { qrSettings in
                    viewModel.startTransaction(qrSettings: qrSettings)
                }
                
            case .showQrCode:
                if let qrString = viewModel.qrCodeToShow {
                    showQrCode(qrString) {
                        viewModel.cancelTransaction()
                        viewModel.reset()
                    }
                }
                
            case .transacting:
                showTransacting {
                    viewModel.cancelTransaction()
                    viewModel.reset()
                }
                
            case .completed:
                showCompleted(viewModel.transactionError) {
                    viewModel.reset()
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}
