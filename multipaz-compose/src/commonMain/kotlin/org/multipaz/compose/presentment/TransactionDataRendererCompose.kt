package org.multipaz.compose.presentment

import androidx.compose.runtime.Composable
import org.multipaz.presentment.TransactionDataContext
import org.multipaz.prompt.TransactionDataRenderer

/**
 * Draws all transaction data items of one [org.multipaz.documenttype.TransactionType] for a credential
 * in the consent prompt.
 */
typealias TransactionDataContent = @Composable (context: TransactionDataContext) -> Unit

/**
 * A [TransactionDataRenderer] drawn with Compose.
 *
 * Add it to a [org.multipaz.prompt.TransactionDataRendererRepository] under the transaction type's
 * identifier and pass the repository to [org.multipaz.prompt.promptModelRequestConsent].
 *
 * @property summary drawn in the consent prompt; should show only what the user needs to decide.
 * @property details drawn on a separate page inside the consent prompt when [summary] calls
 *   [TransactionDataContext.showDetails], or `null` for no details page.
 */
open class TransactionDataRendererCompose(
    val summary: TransactionDataContent,
    val details: TransactionDataContent? = null,
) : TransactionDataRenderer()
