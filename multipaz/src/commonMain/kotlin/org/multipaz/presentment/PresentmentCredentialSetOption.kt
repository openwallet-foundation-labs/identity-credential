package org.multipaz.presentment

/**
 * An element of a credential set that can be presented
 *
 * @property matches the list of matches for this credential set element.
 */
interface PresentmentCredentialSetOption {
    val members: List<PresentmentCredentialSetOptionMember>
}
