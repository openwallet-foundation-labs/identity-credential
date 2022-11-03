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
package com.android.mdl.app.adapter


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.mdl.app.R
import com.android.mdl.app.databinding.ListItemDocumentBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.fragment.SelectDocumentFragmentDirections

/**
 * Adapter for the [RecyclerView].
 */
class DocumentAdapter :
    ListAdapter<Document, RecyclerView.ViewHolder>(DocumentDiffCallback()) {

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

        private fun navigateToDetail(document: Document, view: View) {
            val direction = SelectDocumentFragmentDirections.toDocumentDetail(document)
            if (view.findNavController().currentDestination?.id == R.id.wallet) {
                view.findNavController().navigate(direction)
            }
        }

        fun bind(item: Document) {
            binding.apply {
                document = item
                executePendingBindings()
            }
        }
    }
}

private class DocumentDiffCallback : DiffUtil.ItemCallback<Document>() {

    override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem.identityCredentialName == newItem.identityCredentialName
    }

    override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean {
        return oldItem == newItem
    }
}
