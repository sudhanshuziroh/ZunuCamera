package com.ziroh.zunucamera.edit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.ziroh.zunucamera.BuildConfig
import com.ziroh.zunucamera.R
import kotlinx.coroutines.launch
import ly.img.android.pesdk.PhotoEditorSettingsList
import ly.img.android.pesdk.assets.font.basic.FontPackBasic
import ly.img.android.pesdk.assets.sticker.emoticons.StickerPackEmoticons
import ly.img.android.pesdk.assets.sticker.shapes.StickerPackShapes
import ly.img.android.pesdk.backend.decoder.ImageSource
import ly.img.android.pesdk.backend.model.EditorSDKResult
import ly.img.android.pesdk.backend.model.state.LoadSettings
import ly.img.android.pesdk.backend.model.state.PhotoEditorSaveSettings
import ly.img.android.pesdk.ui.activity.PhotoEditorBuilder
import ly.img.android.pesdk.ui.model.state.UiConfigMainMenu
import ly.img.android.pesdk.ui.model.state.UiConfigSticker
import ly.img.android.pesdk.ui.model.state.UiConfigText
import ly.img.android.pesdk.ui.model.state.UiConfigTheme
import ly.img.android.pesdk.ui.panels.AdjustmentToolPanel
import ly.img.android.pesdk.ui.panels.BrushToolPanel
import ly.img.android.pesdk.ui.panels.FilterToolPanel
import ly.img.android.pesdk.ui.panels.FocusToolPanel
import ly.img.android.pesdk.ui.panels.StickerToolPanel
import ly.img.android.pesdk.ui.panels.TextDesignToolPanel
import ly.img.android.pesdk.ui.panels.TextToolPanel
import ly.img.android.pesdk.ui.panels.TransformToolPanel
import ly.img.android.pesdk.ui.panels.item.PersonalStickerAddItem
import ly.img.android.pesdk.ui.panels.item.ToolItem
import java.io.File

@Suppress("DEPRECATION")
class PhotoEditActivity : AppCompatActivity() {

    private val tools = listOf(
        ToolItem(
            TransformToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.transform.R.string.pesdk_transform_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_transform)
        ),
        ToolItem(
            FilterToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.filter.R.string.pesdk_filter_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_filters)
        ),
        ToolItem(
            AdjustmentToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.adjustment.R.string.pesdk_adjustments_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_adjust)
        ),
        ToolItem(
            FocusToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.focus.R.string.pesdk_focus_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_focus)
        ),
        ToolItem(
            StickerToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.sticker.R.string.pesdk_sticker_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_sticker)
        ),
        ToolItem(
            TextToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.text.R.string.pesdk_text_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_text)
        ),
        ToolItem(
            TextDesignToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.text_design.R.string.pesdk_textDesign_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_text_design)
        ),
        ToolItem(
            BrushToolPanel.TOOL_ID,
            ly.img.android.pesdk.ui.brush.R.string.pesdk_brush_title_name,
            ImageSource.create(ly.img.android.pesdk.ui.R.drawable.imgly_icon_tool_brush)
        )
    )

    private var uri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        openEditor(uri)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun createPesdkSettingsList() =
        PhotoEditorSettingsList(true)
            .configure<UiConfigText> {
                it.setFontList(FontPackBasic.getFontPack())
            }.configure<UiConfigSticker> {
                it.setStickerLists(
                    PersonalStickerAddItem(),
                    StickerPackEmoticons.getStickerCategory(),
                    StickerPackShapes.getStickerCategory()
                )
            }.configure<PhotoEditorSaveSettings> {
                it.jpegQuality = 95
            }.configure<UiConfigTheme> {
                it.theme = R.style.CustomImglyTheme
            }.configure<UiConfigMainMenu> {
                it.setToolList(ArrayList(tools))
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
                    finish()
                    Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
                }

                EditorSDKResult.Status.EXPORT_DONE -> {
                    lifecycleScope.launch {
                        if (result.resultUri != null) {
                            try {
                                val file = result.resultUri?.path?.let { File(it) }
                                val uriToSend = FileProvider.getUriForFile(
                                    this@PhotoEditActivity,
                                    BuildConfig.APPLICATION_ID + ".provider",
                                    file!!
                                )

                                val saveIntent = Intent()
                                saveIntent.data = uriToSend
                                saveIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                setResult(RESULT_OK, saveIntent)
                                finish()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}


