/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.android.identity.wallet.adapter


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.identity.wallet.R
import com.android.identity.wallet.databinding.ListItemDocumentBinding
import com.android.identity.wallet.document.DocumentInformation
import com.android.identity.wallet.wallet.SelectDocumentFragmentDirections

/**
 * Adapter for the [RecyclerView].
 */
class DocumentAdapter : ListAdapter<DocumentInformation, RecyclerView.ViewHolder>(DocumentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return DocumentViewHolder(
            ListItemDocumentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val document = getItem(position)
        (holder as DocumentViewHolder).bind(document)
    }

    class DocumentViewHolder(
        private val binding: ListItemDocumentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.setClickDetailListener {
                binding.document?.let { doc ->
                    navigateToDetail(doc, it)
                }
            }
        }

        private fun navigateToDetail(document: DocumentInformation, view: View) {
            val direction = SelectDocumentFragmentDirections.toDocumentDetail(document.docName)
            if (view.findNavController().currentDestination?.id == R.id.wallet) {
                view.findNavController().navigate(direction)
            }
        }

        fun bind(item: DocumentInformation) {
            binding.apply {
                val cardArt = cardArtFor(item.documentColor)
                binding.llItemContainer.setBackgroundResource(cardArt)
                document = item
                executePendingBindings()
            }
        }

        @DrawableRes
        private fun cardArtFor(cardArt: Int): Int {
            return when (cardArt) {
                1 -> R.drawable.yellow_gradient
                2 -> R.drawable.blue_gradient
                3 -> R.drawable.gradient_red
                else -> R.drawable.green_gradient
            }
        }
    }
}

private class DocumentDiffCallback : DiffUtil.ItemCallback<DocumentInformation>() {

    override fun areItemsTheSame(oldItem: DocumentInformation, newItem: DocumentInformation): Boolean {
        return oldItem.userVisibleName == newItem.userVisibleName
    }

    override fun areContentsTheSame(oldItem: DocumentInformation, newItem: DocumentInformation): Boolean {
        return oldItem == newItem
    }
}
