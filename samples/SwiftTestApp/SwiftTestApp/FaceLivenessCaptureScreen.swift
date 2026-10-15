import SwiftUI
import Multipaz

/**
 * Screen demonstrating the direct embedding of [FaceMatcherLivenessCaptureView] into an
 * application workflow (such as credential provisioning).
 */
struct FaceLivenessCaptureScreen: View {
    @Environment(ViewModel.self) private var viewModel
    var matcherName: String? = nil
    var poses: [FacePose] = [.front]

    @State private var session: FaceMatcherLivenessSession? = nil
    @State private var initializationError: String? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                infoCard

                if let error = initializationError {
                    errorCard(error: error)
                } else if let session {
                    captureCard(session: session)
                } else {
                    ProgressView()
                        .padding(32)
                }

                Button("Cancel") {
                    cancelAndDismiss()
                }
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
            }
            .padding(16)
        }
        .navigationTitle("Check Liveness (View)")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if session == nil && initializationError == nil {
                let matcher = (matcherName != nil ? viewModel.faceMatcherRepository?.lookup(name: matcherName!) : nil)
                    ?? viewModel.faceMatcherRepository?.defaultMatcher
                if let matcher {
                    do {
                        session = try matcher.createLivenessSession(poses: poses)
                    } catch {
                        initializationError = "Failed to initialize liveness session: \(error.localizedDescription)"
                    }
                } else {
                    initializationError = "Face matching is not supported on this platform."
                }
            }
        }
    }

    @ViewBuilder
    private var infoCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Embedded Liveness Verification")
                .font(.headline)
                .fontWeight(.bold)
            Text("This screen embeds the FaceMatcherLivenessCaptureView directly into the screen layout rather than showing a modal bottom sheet dialog. Poses to capture: \(poses.map(\.poseToken).joined(separator: " "))")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func errorCard(error: String) -> some View {
        VStack(spacing: 8) {
            Text(error)
                .font(.body)
                .foregroundColor(.red)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func captureCard(session: FaceMatcherLivenessSession) -> some View {
        VStack {
            FaceMatcherLivenessCaptureView(
                session: session,
                onSuccess: { capturedImages in
                    viewModel.path.append(.fakeIssuerApprovalScreen(capturedImages: capturedImages))
                },
                onFailed: {
                    cancelAndDismiss()
                }
            )
        }
        .frame(maxWidth: .infinity)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(.separator), lineWidth: 1)
        )
    }

    private func cancelAndDismiss() {
        session?.cancel()
        if !viewModel.path.isEmpty {
            viewModel.path.removeLast()
        }
    }
}

extension FacePose {
    var poseToken: String {
        switch self {
        case .front: return "FRONT"
        case .lookLeft: return "LOOK_LEFT"
        case .lookRight: return "LOOK_RIGHT"
        case .lookUp: return "LOOK_UP"
        case .lookDown: return "LOOK_DOWN"
        default: return "\(self)"
        }
    }

    static func fromToken(_ token: String) -> FacePose? {
        switch token.uppercased() {
        case "FRONT": return .front
        case "LOOK_LEFT": return .lookLeft
        case "LOOK_RIGHT": return .lookRight
        case "LOOK_UP": return .lookUp
        case "LOOK_DOWN": return .lookDown
        default: return nil
        }
    }

    var displayLabel: String {
        switch self {
        case .front: return "Front"
        case .lookLeft: return "Look Left"
        case .lookRight: return "Look Right"
        case .lookUp: return "Look Up"
        case .lookDown: return "Look Down"
        default: return "\(self)"
        }
    }
}
