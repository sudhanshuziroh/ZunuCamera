package com.ziroh.zunucamera.edit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ziroh.zunucamera.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ly.img.android.pesdk.VideoEditorSettingsList
import ly.img.android.pesdk.assets.filter.basic.FilterPackBasic
import ly.img.android.pesdk.assets.font.basic.FontPackBasic
import ly.img.android.pesdk.assets.frame.basic.FramePackBasic
import ly.img.android.pesdk.assets.overlay.basic.OverlayPackBasic
import ly.img.android.pesdk.assets.sticker.animated.StickerPackAnimated
import ly.img.android.pesdk.assets.sticker.emoticons.StickerPackEmoticons
import ly.img.android.pesdk.assets.sticker.shapes.StickerPackShapes
import ly.img.android.pesdk.backend.model.EditorSDKResult
import ly.img.android.pesdk.backend.model.state.LoadSettings
import ly.img.android.pesdk.ui.activity.VideoEditorBuilder
import ly.img.android.pesdk.ui.model.state.UiConfigFilter
import ly.img.android.pesdk.ui.model.state.UiConfigFrame
import ly.img.android.pesdk.ui.model.state.UiConfigOverlay
import ly.img.android.pesdk.ui.model.state.UiConfigSticker
import ly.img.android.pesdk.ui.model.state.UiConfigText
import ly.img.android.pesdk.ui.panels.item.PersonalStickerAddItem

@Suppress("DEPRECATION")
class VideoEditActivity: AppCompatActivity() {

    private var uri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uri = intent.data
        uri?.let {
            openEditor(it)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun createVESDKSettingsList() =
        VideoEditorSettingsList(true)
            .configure<UiConfigFilter> {
                it.setFilterList(FilterPackBasic.getFilterPack())
            }
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
                    StickerPackShapes.getStickerCategory(),
                    StickerPackAnimated.getStickerCategory()
                )
            }

    private fun openEditor(inputSource: Uri) {
        val settingsList = createVESDKSettingsList()

        settingsList.configure<LoadSettings> {
            it.source = inputSource
        }

        VideoEditorBuilder(this)
            .setSettingsList(settingsList)
            .startActivityForResult(this, 1)

        settingsList.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        intent ?: return
        if (resultCode == RESULT_OK && requestCode == 1) {
            val data = EditorSDKResult(intent)

            lifecycleScope.launch {
                if(uri != null && data.resultUri != null) {
                    FileUtils.copyUriContent(
                        destinationUri = uri!!,
                        sourceUri = data.resultUri!!,
                        contentResolver = contentResolver
                    )
                    withContext(Dispatchers.Main) {
                        finish()
                    }
                }
            }
        } else if (resultCode == RESULT_CANCELED && requestCode == 1) {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}