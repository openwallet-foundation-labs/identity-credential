package org.multipaz.util


/**
 * Given a list (X0, X1, X2, ..., Xn, ...) generates a number of lists of the same length
 * where each the list element in the `n`th position can assume values `0` up to `Xn`
 *
 * @returns a list with all combinations as described above.
 */
fun List<Int>.generateAllPaths(): List<List<Int>> {
    if (isEmpty()) {
        return listOf(emptyList())
    }
    val allPaths = mutableListOf<List<Int>>()
    generate(0, MutableList(size) { 0 }, allPaths, this)
    return allPaths
}

private fun generate(
    index: Int,
    currentPath: MutableList<Int>,
    allPaths: MutableList<List<Int>>,
    maxPath: List<Int>
) {
    if (index == maxPath.size) {
        allPaths.add(currentPath.toList())
        return
    }
    for (value in 0 until maxPath[index]) {
        currentPath[index] = value
        generate(index + 1, currentPath, allPaths, maxPath)
    }
}
