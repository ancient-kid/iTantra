package com.itantra.app.models

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.itantra.stt.databinding.ActivityModelsBinding
import com.itantra.stt.databinding.ItemModelPackBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Lets the user see every language pack, and choose which ones to install.
 *
 * Nothing downloads on its own: the app ships with English working offline and
 * every other pack is an explicit user action here.
 */
class ModelsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelsBinding
    private lateinit var adapter: PackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PackAdapter(
            packs = ModelStore.downloadablePacks(this),
            onPrimaryAction = ::onPrimaryAction
        )
        binding.recyclerPacks.layoutManager = LinearLayoutManager(this)
        binding.recyclerPacks.adapter = adapter

        ModelDownloadManager.refresh(this)

        lifecycleScope.launch {
            ModelDownloadManager.statuses.collectLatest { statuses ->
                adapter.updateStatuses(statuses)
                renderStorageSummary()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ModelDownloadManager.refresh(this)
        renderStorageSummary()
    }

    private fun onPrimaryAction(pack: ModelPack) {
        when (ModelDownloadManager.statusOf(pack.id)) {
            is ModelDownloadManager.Status.Downloading,
            ModelDownloadManager.Status.Verifying -> ModelDownloadManager.cancel(pack.id)

            ModelDownloadManager.Status.Installed -> confirmDelete(pack)

            else -> {
                if (pack.isNonCommercial) confirmNonCommercial(pack)
                else ModelDownloadManager.download(this, pack)
            }
        }
    }

    private fun confirmNonCommercial(pack: ModelPack) {
        AlertDialog.Builder(this)
            .setTitle("Non-commercial voice")
            .setMessage(
                "${pack.displayName} uses a Meta MMS voice licensed CC-BY-NC 4.0. " +
                    "It is fine for research, demos and evaluation, but may not be used " +
                    "in a commercial deployment.\n\nDownload it?"
            )
            .setPositiveButton("Download") { _, _ ->
                ModelDownloadManager.download(this, pack)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(pack: ModelPack) {
        AlertDialog.Builder(this)
            .setTitle("Remove ${pack.displayName}?")
            .setMessage(
                "Frees ${formatBytes(pack.totalBytes)}. You will need internet to " +
                    "download it again."
            )
            .setPositiveButton("Remove") { _, _ ->
                ModelDownloadManager.delete(this, pack)
                renderStorageSummary()
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun renderStorageSummary() {
        val packs = ModelStore.downloadablePacks(this)
        val installed = packs.count { ModelStore.isInstalled(this, it) }
        binding.tvStorageSummary.text = String.format(
            Locale.US,
            "%d of %d packs installed  ·  %s on device",
            installed, packs.size, formatBytes(ModelStore.bytesOnDisk(this))
        )
    }

    // ------------------------------------------------------------------

    private class PackAdapter(
        private val packs: List<ModelPack>,
        private val onPrimaryAction: (ModelPack) -> Unit
    ) : RecyclerView.Adapter<PackAdapter.PackViewHolder>() {

        private var statuses: Map<String, ModelDownloadManager.Status> = emptyMap()

        fun updateStatuses(next: Map<String, ModelDownloadManager.Status>) {
            statuses = next
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder =
            PackViewHolder(
                ItemModelPackBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

        override fun getItemCount(): Int = packs.size

        override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
            val pack = packs[position]
            holder.bind(
                pack,
                statuses[pack.id] ?: ModelDownloadManager.Status.NotInstalled,
                onPrimaryAction
            )
        }

        class PackViewHolder(
            private val binding: ItemModelPackBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(
                pack: ModelPack,
                status: ModelDownloadManager.Status,
                onPrimaryAction: (ModelPack) -> Unit
            ) {
                binding.tvPackName.text = pack.displayName
                binding.tvPackDetail.text = String.format(
                    Locale.US,
                    "%s · %s · %s",
                    pack.kind.name,
                    pack.modelName,
                    formatBytes(pack.totalBytes)
                )

                binding.tvPackLicense.visibility =
                    if (pack.isNonCommercial) View.VISIBLE else View.GONE
                binding.tvPackLicense.text = "NON-COMMERCIAL (${pack.license})"

                when (status) {
                    is ModelDownloadManager.Status.Downloading -> {
                        binding.progressPack.visibility = View.VISIBLE
                        binding.progressPack.progress = (status.fraction * 100).toInt()
                        binding.tvPackStatus.text = String.format(
                            Locale.US,
                            "Downloading %s of %s (%d%%)",
                            formatBytes(status.bytes),
                            formatBytes(status.total),
                            (status.fraction * 100).toInt()
                        )
                        binding.btnPackAction.text = "Cancel"
                    }

                    ModelDownloadManager.Status.Verifying -> {
                        binding.progressPack.visibility = View.VISIBLE
                        binding.progressPack.isIndeterminate = true
                        binding.tvPackStatus.text = "Verifying checksum…"
                        binding.btnPackAction.text = "Cancel"
                    }

                    ModelDownloadManager.Status.Installed -> {
                        binding.progressPack.visibility = View.GONE
                        binding.tvPackStatus.text = "Installed — available offline"
                        binding.btnPackAction.text = "Remove"
                    }

                    is ModelDownloadManager.Status.Failed -> {
                        binding.progressPack.visibility = View.GONE
                        binding.tvPackStatus.text = "Failed: ${status.message}"
                        binding.btnPackAction.text = "Retry"
                    }

                    ModelDownloadManager.Status.NotInstalled -> {
                        binding.progressPack.visibility = View.GONE
                        binding.progressPack.isIndeterminate = false
                        binding.tvPackStatus.text = "Not installed"
                        binding.btnPackAction.text = "Download"
                    }
                }

                binding.btnPackAction.setOnClickListener { onPrimaryAction(pack) }
            }
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_048_576L -> String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

private fun formatBytes(bytes: Long): String = ModelsActivity.formatBytes(bytes)
