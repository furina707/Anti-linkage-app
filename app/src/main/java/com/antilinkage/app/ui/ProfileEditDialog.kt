package com.antilinkage.app.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import com.antilinkage.app.databinding.DialogEditFingerprintBinding
import com.antilinkage.fingerprint.config.InstanceFingerprint
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ProfileEditDialog {

    fun show(
        context: Context,
        current: InstanceFingerprint,
        onSave: (InstanceFingerprint) -> Unit
    ) {
        val binding = DialogEditFingerprintBinding.inflate(LayoutInflater.from(context))

        fun fillUI(fp: InstanceFingerprint) {
            binding.etBrand.setText(fp.brand)
            binding.etModel.setText(fp.model)
            binding.etAndroidId.setText(fp.androidId)
            binding.etImei.setText(fp.imei)
            binding.etMac.setText(fp.macAddress)
            binding.etLatitude.setText(fp.latitude.toString())
            binding.etLongitude.setText(fp.longitude.toString())
        }

        fillUI(current)

        var workingFp = current.copy()

        binding.btnRandomize.setOnClickListener {
            val randomBrands = listOf("Xiaomi", "Samsung", "Google", "OnePlus", "Huawei")
            val randomModels = listOf("Mi 13", "Galaxy S23", "Pixel 8 Pro", "OnePlus 11", "Mate 60")
            val index = (randomBrands.indices).random()

            workingFp = workingFp.copy(
                brand = randomBrands[index],
                model = randomModels[index],
                androidId = InstanceFingerprint.generateRandomHex(16),
                imei = InstanceFingerprint.generateRandomNumeric(15),
                macAddress = InstanceFingerprint.generateRandomMac(),
                serial = InstanceFingerprint.generateRandomAlphanumeric(10)
            )
            fillUI(workingFp)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setPositiveButton("保存") { _, _ ->
                val updated = workingFp.copy(
                    brand = binding.etBrand.text.toString().trim(),
                    model = binding.etModel.text.toString().trim(),
                    androidId = binding.etAndroidId.text.toString().trim(),
                    imei = binding.etImei.text.toString().trim(),
                    macAddress = binding.etMac.text.toString().trim(),
                    latitude = binding.etLatitude.text.toString().toDoubleOrNull() ?: workingFp.latitude,
                    longitude = binding.etLongitude.text.toString().toDoubleOrNull() ?: workingFp.longitude
                )
                onSave(updated)
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
    }
}
