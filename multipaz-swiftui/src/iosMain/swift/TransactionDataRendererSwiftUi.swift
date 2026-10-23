import SwiftUI

/// Draws all transaction data items of one transaction type for a credential in the consent prompt.
public typealias TransactionDataContent = (_ context: TransactionDataContext) -> AnyView

/// A ``TransactionDataRenderer`` drawn with SwiftUI.
///
/// Add it to a ``TransactionDataRendererRepository`` under the transaction type's identifier and pass
/// the repository to `promptModelRequestConsent()`, or to ``Consent`` or ``RequestAuthorizationView``
/// when showing those directly.
open class TransactionDataRendererSwiftUi: TransactionDataRenderer {
    /// Drawn in the consent prompt; should show only what the user needs to decide.
    public let summary: TransactionDataContent

    /// Drawn on a separate page inside the consent prompt when `summary` calls
    /// `TransactionDataContext.showDetails`, or `nil` for no details page.
    public let details: TransactionDataContent?

    public init(summary: @escaping TransactionDataContent, details: TransactionDataContent? = nil) {
        self.summary = summary
        self.details = details
        super.init()
    }
}

/// One block of transaction data in the consent prompt, drawn either by the application or by the library.
enum TransactionDataBlock {
    /// All items of a transaction type the application has a ``TransactionDataRendererSwiftUi`` for, drawn together.
    case wallet(identifier: String, items: [TransactionData<AnyObject>], renderer: TransactionDataRendererSwiftUi)

    /// A single item drawn by the built-in rendering.
    case builtIn(item: TransactionData<AnyObject>)
}

/// Splits the transaction data of a credential into the blocks the consent prompt draws.
///
/// Items of a transaction type with a ``TransactionDataRendererSwiftUi`` in the repository are drawn
/// together, in the place of the first of them. Every other item keeps the built-in rendering, one
/// item at a time, in request order. Renderers made for another UI toolkit are ignored.
func transactionDataBlocks(
    _ transactionData: [TransactionData<AnyObject>],
    repository: TransactionDataRendererRepository?
) -> [TransactionDataBlock] {
    var seenIdentifiers = Set<String>()
    var renderers: [String: TransactionDataRendererSwiftUi] = [:]
    var blocks: [TransactionDataBlock] = []
    for item in transactionData {
        let identifier = item.type.identifier
        let isFirstOfType = seenIdentifiers.insert(identifier).inserted
        if isFirstOfType,
           let renderer = repository?.getRenderer(identifier: identifier) as? TransactionDataRendererSwiftUi {
            renderers[identifier] = renderer
        }
        if let renderer = renderers[identifier] {
            if isFirstOfType {
                blocks.append(.wallet(
                    identifier: identifier,
                    items: transactionData.filter { $0.type.identifier == identifier },
                    renderer: renderer
                ))
            }
        } else {
            blocks.append(.builtIn(item: item))
        }
    }
    return blocks
}
