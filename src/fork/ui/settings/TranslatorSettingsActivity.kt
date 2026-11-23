package desu.inugram.ui.settings

import android.view.View
import android.content.Intent
import android.net.Uri
import android.text.InputType
import androidx.core.content.edit
import desu.inugram.InuConfig
import desu.inugram.SearchRegistry
import desu.inugram.helpers.InuUtils
// #if PLUGINS
import desu.inugram.helpers.plugins.telegram.PluginTranslation
// #endif
import desu.inugram.helpers.translate.TranslationProviderHelper
import desu.inugram.helpers.translate.ExternalTranslateHelper
import desu.inugram.helpers.translate.TranslationSecrets
import desu.inugram.ui.settings.TranslationTargetActivity
import desu.inugram.ui.showInputDialog
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.TranslateAlert2
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.PremiumPreviewFragment
import org.telegram.ui.RestrictedLanguagesSelectActivity

class TranslatorSettingsActivity : SettingsPageActivity() {
    override fun getTitle(): CharSequence = LocaleController.getString(R.string.InuTranslator)

    private val translateController get() = MessagesController.getInstance(currentAccount).translateController

    override fun onResume() {
        super.onResume()
        listView?.adapter?.update(true)
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(
            UItem.asButton(
                BUTTON_PROVIDER,
                "مترجم پیش‌فرض",
                providerLabel(),
            )
        )
        items.add(UItem.asShadow("گوگل: رایگان و بدون نیاز به کلید API. برای استفاده، «Google Translate» را به‌عنوان مترجم انتخاب کن."))
        items.add(UItem.asButton(BUTTON_GOOGLE_GUIDE, "راهنمای Google Translate", "مشاهده توضیحات"))
        items.add(UItem.asButton(BUTTON_MICROSOFT_KEY, "کلید API مترجم مایکروسافت",
            if (TranslationSecrets.get("microsoft_key").isBlank()) "تنظیم نشده" else "ثبت شده"))
        items.add(UItem.asButton(BUTTON_MICROSOFT_REGION, "ناحیه (Region) مایکروسافت",
            TranslationSecrets.get("microsoft_region").ifBlank { "برای برخی منابع لازم است" }))
        items.add(UItem.asShadow("برای دریافت کلید: وارد Azure Portal شو، یک منبع Translator بساز و از بخش Keys and Endpoint کلید را کپی کن. اگر هنگام ساخت منبع Region نمایش داده شد، همان مقدار را اینجا وارد کن."))
        items.add(UItem.asButton(BUTTON_MICROSOFT_GUIDE, "راهنمای دریافت کلید Microsoft", "بازکردن راهنمای رسمی"))
        items.add(UItem.asButton(BUTTON_OPENAI_KEY, "کلید API چت‌جی‌پی‌تی (OpenAI)",
            if (TranslationSecrets.get("openai_key").isBlank()) "تنظیم نشده" else "ثبت شده"))
        items.add(UItem.asButton(BUTTON_OPENAI_MODEL, "مدل OpenAI",
            TranslationSecrets.get("openai_model").ifBlank { "gpt-4.1-mini" }))
        items.add(UItem.asShadow("برای دریافت کلید: وارد حساب OpenAI شو، در صفحه API Keys گزینه ساخت کلید جدید را بزن و کلید را کپی کن. برای استفاده از API باید پرداخت و اعتبار API جداگانه تنظیم شده باشد؛ اشتراک ChatGPT کافی نیست."))
        items.add(UItem.asButton(BUTTON_OPENAI_GUIDE, "راهنمای دریافت کلید OpenAI", "بازکردن صفحه ساخت کلید"))
        items.add(UItem.asShadow("توجه: با انتخاب مایکروسافت یا OpenAI، متن پیام برای ترجمه به همان سرویس ارسال می‌شود و ممکن است هزینه API داشته باشد. کلیدها روی این دستگاه رمزگذاری می‌شوند و در خروجی تنظیمات قرار نمی‌گیرند."))
        items.add(
            UItem.asCheck(
                TOGGLE_SHOW_TRANSLATE_BUTTON,
                LocaleController.getString(R.string.ShowTranslateButton),
            ).setChecked(translateController.isContextTranslateEnabled)
        )
        items.add(
            UItem.asCheck(
                TOGGLE_SHOW_TRANSLATE_CHAT_BUTTON,
                LocaleController.getString(R.string.ShowTranslateChatButton),
            ).setChecked(translateController.isChatTranslateEnabled)
                .setLocked(!TranslationProviderHelper.canTranslateChats(currentAccount))
                .onBind(Utilities.Callback { view ->
                    (view as TextCheckCell).setCheckBoxIcon(
                        if (TranslationProviderHelper.canTranslateChats(currentAccount)) 0 else R.drawable.permission_locked
                    )
                })
        )
        items.add(
            UItem.asButton(
                BUTTON_TARGET_LANG,
                LocaleController.getString(R.string.InuTranslationTarget),
                targetLangLabel(),
            )
        )
        items.add(
            UItem.asButton(
                BUTTON_DO_NOT_TRANSLATE,
                LocaleController.getString(R.string.DoNotTranslate),
                doNotTranslateLabel(),
            )
        )
        items.add(UItem.asShadow(null))
        items.add(
            UItem.asCheck(
                TOGGLE_IN_PLACE_TRANSLATION,
                LocaleController.getString(R.string.InuInPlaceTranslation),
            ).setChecked(InuConfig.IN_PLACE_TRANSLATION.value)
        )
        items.add(
            UItem.asCheck(
                TOGGLE_TRANSLATE_WEB_PREVIEWS,
                LocaleController.getString(R.string.InuTranslateWebPreviews),
            ).setChecked(InuConfig.TRANSLATE_WEB_PREVIEWS.value)
        )
        items.add(
            UItem.asCheck(
                TOGGLE_AUTO_TRANSLATE_CHANNELS,
                LocaleController.getString(R.string.InuAutoTranslateChannels),
            ).setChecked(InuConfig.AUTO_TRANSLATE_CHANNELS.value)
        )
        items.add(UItem.asShadow(LocaleController.getString(R.string.InuAutoTranslateChannelsInfo)))
        items.add(
            UItem.asCheck(
                TOGGLE_KEEP_ORIGINAL,
                LocaleController.getString(R.string.InuKeepOriginalAfterTranslation),
            ).setChecked(InuConfig.KEEP_ORIGINAL_AFTER_TRANSLATION.value)
        )
        items.add(
            UItem.asCheck(
                TOGGLE_AUTO_DETECT_LANG,
                LocaleController.getString(R.string.InuTranslateAutoDetectLang),
            ).setChecked(InuConfig.TRANSLATE_AUTO_DETECT_LANG.value)
        )
        items.add(UItem.asShadow(LocaleController.getString(R.string.InuTranslateAutoDetectLangInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            TOGGLE_IN_PLACE_TRANSLATION -> {
                val new = InuConfig.IN_PLACE_TRANSLATION.toggle()
                (view as? TextCheckCell)?.isChecked = new
            }

            TOGGLE_TRANSLATE_WEB_PREVIEWS -> {
                val new = InuConfig.TRANSLATE_WEB_PREVIEWS.toggle()
                (view as? TextCheckCell)?.isChecked = new
            }

            TOGGLE_AUTO_TRANSLATE_CHANNELS -> {
                val new = InuConfig.AUTO_TRANSLATE_CHANNELS.toggle()
                if (new) {
                    val globalSettings = MessagesController.getGlobalMainSettings()
                    if (!globalSettings.contains("translate_to_language")) {
                        globalSettings.edit { putString("translate_to_language", "fa") }
                    }
                }
                (view as? TextCheckCell)?.isChecked = new
            }

            TOGGLE_KEEP_ORIGINAL -> {
                val new = InuConfig.KEEP_ORIGINAL_AFTER_TRANSLATION.toggle()
                (view as? TextCheckCell)?.isChecked = new
                NotificationCenter.getInstance(currentAccount)
                    .postNotificationName(NotificationCenter.updateInterfaces, 0)
            }

            TOGGLE_AUTO_DETECT_LANG -> {
                val new = InuConfig.TRANSLATE_AUTO_DETECT_LANG.toggle()
                (view as? TextCheckCell)?.isChecked = new
            }

            TOGGLE_SHOW_TRANSLATE_BUTTON -> {
                val new = !translateController.isContextTranslateEnabled
                translateController.isContextTranslateEnabled = new
                (view as? TextCheckCell)?.isChecked = new
                NotificationCenter.getInstance(currentAccount)
                    .postNotificationName(NotificationCenter.updateSearchSettings)
            }

            TOGGLE_SHOW_TRANSLATE_CHAT_BUTTON -> {
                val new = !translateController.isChatTranslateEnabled
                if (new && !TranslationProviderHelper.canTranslateChats(currentAccount)) {
                    showDialog(
                        PremiumFeatureBottomSheet(
                            this,
                            PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS,
                            false,
                        )
                    )
                    return
                }
                translateController.isChatTranslateEnabled = new
                (view as? TextCheckCell)?.isChecked = new
                NotificationCenter.getInstance(currentAccount)
                    .postNotificationName(NotificationCenter.updateSearchSettings)
            }

            BUTTON_DO_NOT_TRANSLATE -> presentFragment(RestrictedLanguagesSelectActivity())

            BUTTON_PROVIDER -> showProviderDialog()
            BUTTON_GOOGLE_GUIDE -> openHelp("https://translate.google.com/")
            BUTTON_MICROSOFT_GUIDE -> openHelp("https://learn.microsoft.com/azure/ai-services/translator/create-translator-resource")
            BUTTON_OPENAI_GUIDE -> openHelp("https://platform.openai.com/api-keys")
            BUTTON_MICROSOFT_KEY -> editSecret("کلید API مترجم مایکروسافت", "microsoft_key", true)
            BUTTON_MICROSOFT_REGION -> editSecret("ناحیه (Region) مایکروسافت", "microsoft_region", false)
            BUTTON_OPENAI_KEY -> editSecret("کلید API چت‌جی‌پی‌تی (OpenAI)", "openai_key", true)
            BUTTON_OPENAI_MODEL -> editSecret("نام مدل OpenAI", "openai_model", false)

            BUTTON_TARGET_LANG -> presentFragment(TranslationTargetActivity())
        }
    }

    private fun showProviderDialog() {
        val providers = mutableListOf(
            RadioDialogBuilder.Item("Google Translate"),
            RadioDialogBuilder.Item("Microsoft Translator"),
            RadioDialogBuilder.Item("چت‌جی‌پی‌تی (OpenAI)"),
        )
        // #if PLUGINS
        PluginTranslation.providers.forEach {
            providers.add(RadioDialogBuilder.Item(it.name,
                LocaleController.formatString(R.string.InuTranslationProviderFromPlugin, it.session.manifest.name)))
        }
        // #endif
        val selected = InuConfig.TRANSLATION_PROVIDER.value
        val selectedIndex = when (selected) {
            "" -> 0
            ExternalTranslateHelper.MICROSOFT -> 1
            ExternalTranslateHelper.OPENAI -> 2
            else -> {
                // #if PLUGINS
                PluginTranslation.providers.indexOfFirst { it.key == selected }.let { if (it < 0) -1 else it + 3 }
                // #else
                -1
                // #endif
            }
        }
        val dialog = RadioDialogBuilder(context, resourceProvider)
            .setTitle("انتخاب مترجم")
            .setItems(providers, selectedIndex) { _, index ->
                InuConfig.TRANSLATION_PROVIDER.value = when (index) {
                    0 -> ""
                    1 -> ExternalTranslateHelper.MICROSOFT
                    2 -> ExternalTranslateHelper.OPENAI
                    else -> {
                        // #if PLUGINS
                        PluginTranslation.providers.getOrNull(index - 3)?.key ?: ""
                        // #else
                        ""
                        // #endif
                    }
                }
                listView?.adapter?.update(true)
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings)
            }
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .create()
        showDialog(dialog)
    }

    private fun providerLabel(): String = when (InuConfig.TRANSLATION_PROVIDER.value) {
        "" -> "Google Translate"
        ExternalTranslateHelper.MICROSOFT -> "Microsoft Translator"
        ExternalTranslateHelper.OPENAI -> "چت‌جی‌پی‌تی (OpenAI)"
        else -> {
            // #if PLUGINS
            PluginTranslation.findProvider(InuConfig.TRANSLATION_PROVIDER.value)?.name
                ?: LocaleController.getString(R.string.InuTranslationProviderUnavailable)
            // #else
            LocaleController.getString(R.string.InuTranslationProviderUnavailable)
            // #endif
        }
    }

    private fun openHelp(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: Exception) {
            org.telegram.messenger.FileLog.e("Unable to open translation provider help", error)
        }
    }

    private fun editSecret(title: String, key: String, password: Boolean) {
        val inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT
        showInputDialog(context, resourceProvider, title,
            if (password) "کلید API را وارد یا جای‌گذاری کن" else "مقدار را وارد کن؛ برای پاک‌کردن خالی بگذار",
            if (password) null else TranslationSecrets.get(key),
            inputType = inputType,
            showDialog = { showDialog(it) },
        ) { value ->
            TranslationSecrets.set(key, value)
            listView?.adapter?.update(true)
            true
        }
    }

    private fun targetLangLabel(): String {
        if (!MessagesController.getGlobalMainSettings().contains("translate_to_language")) {
            return LocaleController.getString(R.string.InuTranslationTargetFollowApp)
        }
        val code = TranslateAlert2.getToLanguage()
        return TranslateAlert2.languageName(code)?.let { TranslateAlert2.capitalFirst(it) } ?: code.uppercase()
    }

    private fun doNotTranslateLabel(): String {
        val langs = RestrictedLanguagesSelectActivity.getRestrictedLanguages()
        if (langs.isEmpty()) return LocaleController.getString(R.string.None)
        if (langs.size > 2) return LocaleController.formatPluralString("Languages", langs.size)
        return langs.joinToString(", ") {
            TranslateAlert2.languageName(it)?.let { name -> TranslateAlert2.capitalFirst(name) } ?: it.uppercase()
        }
    }

    companion object {
        private val TOGGLE_IN_PLACE_TRANSLATION = InuUtils.generateId()
        private val TOGGLE_TRANSLATE_WEB_PREVIEWS = InuUtils.generateId()
        private val TOGGLE_AUTO_TRANSLATE_CHANNELS = InuUtils.generateId()
        private val TOGGLE_KEEP_ORIGINAL = InuUtils.generateId()
        private val TOGGLE_AUTO_DETECT_LANG = InuUtils.generateId()
        private val TOGGLE_SHOW_TRANSLATE_BUTTON = InuUtils.generateId()
        private val TOGGLE_SHOW_TRANSLATE_CHAT_BUTTON = InuUtils.generateId()
        private val BUTTON_DO_NOT_TRANSLATE = InuUtils.generateId()
        private val BUTTON_TARGET_LANG = InuUtils.generateId()
        private val BUTTON_PROVIDER = InuUtils.generateId()
        private val BUTTON_GOOGLE_GUIDE = InuUtils.generateId()
        private val BUTTON_MICROSOFT_GUIDE = InuUtils.generateId()
        private val BUTTON_OPENAI_GUIDE = InuUtils.generateId()
        private val BUTTON_MICROSOFT_KEY = InuUtils.generateId()
        private val BUTTON_MICROSOFT_REGION = InuUtils.generateId()
        private val BUTTON_OPENAI_KEY = InuUtils.generateId()
        private val BUTTON_OPENAI_MODEL = InuUtils.generateId()

        @JvmField val PAGE = SearchRegistry.Page(
            slug = "translator",
            titleRes = R.string.InuTranslator,
            iconRes = R.drawable.msg_translate,
            factory = ::TranslatorSettingsActivity,
            entries = listOf(
                SearchRegistry.Entry("translation-provider", R.string.InuTranslationProvider, BUTTON_PROVIDER),
                SearchRegistry.Entry("microsoft-translator-key", R.string.InuTranslator, BUTTON_MICROSOFT_KEY),
                SearchRegistry.Entry("openai-translation-key", R.string.InuTranslator, BUTTON_OPENAI_KEY),
                SearchRegistry.Entry("show-translate-button", R.string.ShowTranslateButton, TOGGLE_SHOW_TRANSLATE_BUTTON),
                SearchRegistry.Entry("show-translate-chat-button", R.string.ShowTranslateChatButton, TOGGLE_SHOW_TRANSLATE_CHAT_BUTTON),
                SearchRegistry.Entry("translation-target", R.string.InuTranslationTarget, BUTTON_TARGET_LANG),
                SearchRegistry.Entry("do-not-translate", R.string.DoNotTranslate, BUTTON_DO_NOT_TRANSLATE),
                SearchRegistry.Entry("in-place-translation", R.string.InuInPlaceTranslation, TOGGLE_IN_PLACE_TRANSLATION),
                SearchRegistry.Entry("translate-web-previews", R.string.InuTranslateWebPreviews, TOGGLE_TRANSLATE_WEB_PREVIEWS),
                SearchRegistry.Entry("auto-translate-channels", R.string.InuAutoTranslateChannels, TOGGLE_AUTO_TRANSLATE_CHANNELS),
                SearchRegistry.Entry("keep-original-after-translation", R.string.InuKeepOriginalAfterTranslation, TOGGLE_KEEP_ORIGINAL),
                SearchRegistry.Entry("translate-auto-detect-lang", R.string.InuTranslateAutoDetectLang, TOGGLE_AUTO_DETECT_LANG),
            ),
        )
    }
}
