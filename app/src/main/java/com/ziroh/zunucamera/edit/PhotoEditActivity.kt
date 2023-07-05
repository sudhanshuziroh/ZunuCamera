package com.ziroh.zunucamera.edit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ziroh.zunucamera.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.img.android.pesdk.PhotoEditorSettingsList
import ly.img.android.pesdk.assets.font.basic.FontPackBasic
import ly.img.android.pesdk.assets.frame.basic.FramePackBasic
import ly.img.android.pesdk.assets.overlay.basic.OverlayPackBasic
import ly.img.android.pesdk.assets.sticker.emoticons.StickerPackEmoticons
import ly.img.android.pesdk.assets.sticker.shapes.StickerPackShapes
import ly.img.android.pesdk.backend.model.EditorSDKResult
import ly.img.android.pesdk.backend.model.state.LoadSettings
import ly.img.android.pesdk.backend.model.state.PhotoEditorSaveSettings
import ly.img.android.pesdk.ui.activity.PhotoEditorBuilder
import ly.img.android.pesdk.ui.model.state.UiConfigFrame
import ly.img.android.pesdk.ui.model.state.UiConfigOverlay
import ly.img.android.pesdk.ui.model.state.UiConfigSticker
import ly.img.android.pesdk.ui.model.state.UiConfigText
import ly.img.android.pesdk.ui.panels.item.PersonalStickerAddItem
@Suppress("DEPRECATION")
class PhotoEditActivity : AppCompatActivity() {

    private var uri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = intent.data

        uri?.let {
            openEditor(it)
        }
    }


    private fun createPesdkSettingsList() =
        PhotoEditorSettingsList(true)
            .configure<UiConfigText> {
                it.setFontList(FontPackBasic.getFontPack())
            }
            .configure<UiConfigFrame> {
                it.setFrameList(FramePackBasic.getFramePack())
            }
            .configure<UiConfigOverlay> {
                it.setOverlayList(OverlayPackBasic.getOverlayPack())
            }
            .configure<UiConfigSticker> {
                it.setStickerLists(
                    PersonalStickerAddItem(),
                    StickerPackEmoticons.getStickerCategory(),
                    StickerPackShapes.getStickerCategory()
                )
            }.configure<PhotoEditorSaveSettings> {
                it.jpegQuality = 95
            }

    private fun openEditor(inputImage: Uri?) {
        val settingsList = createPesdkSettingsList()
        settingsList.configure<LoadSettings> {
            it.source = inputImage
        }

        PhotoEditorBuilder(this)
            .setSettingsList(settingsList)
            .startActivityForResult(this, 1)

        settingsList.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (intent == null) {
            finish()
            return
        }
        if (requestCode == 1) {
            val result = EditorSDKResult(intent)
            when (result.resultStatus) {
                EditorSDKResult.Status.CANCELED -> {
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                    finish()
                }

                EditorSDKResult.Status.EXPORT_DONE -> {
                    lifecycleScope.launch {
                        if (uri != null && result.resultUri != null) {

                            FileUtils.copyUriContent(
                                destinationUri = uri!!,
                                sourceUri = result.resultUri!!,
                                contentResolver = contentResolver
                            )
                            withContext(Dispatchers.Main) {
                                finish()
                            }

                        }
                    }
                }

                else -> Unit
            }
        }
    }
}


