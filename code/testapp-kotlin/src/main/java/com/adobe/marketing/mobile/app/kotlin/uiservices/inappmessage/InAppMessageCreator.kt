package com.adobe.marketing.mobile.app.kotlin.uiservices.inappmessage

import com.adobe.marketing.mobile.app.kotlin.presentationUtilityProvider
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.services.ui.InAppMessage
import com.adobe.marketing.mobile.services.ui.Presentable
import com.adobe.marketing.mobile.services.ui.PresentationError
import com.adobe.marketing.mobile.services.ui.message.InAppMessageEventListener
import com.adobe.marketing.mobile.services.ui.message.InAppMessageSettings

object InAppMessageCreator {
    private const val LOG_TAG = "InAppMessageCreator"

    private const val sampleHTML = "<html lang=\"en\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <script type=\"text/javascript\">\n" +
            "            function callNative(action) {\n" +
            "                try {\n" +
            "                    // the name of the message handler is the same name that must be registered in native code.\n" +
            "                    // in this case the message name is \"Android\"\n" +
            "                    Android.run(action);\n" +
            "                } catch(err) {\n" +
            "                    console.log(err); }\n" +
            "                }\n" +
            "            </script>" +
            "    <title>Responsive Webpage</title>\n" +
            "</head>\n" +
            "<body style=\"margin: 0; font-family: Arial, sans-serif; background-color: black; color: white;\">\n" +
            "\n" +
            "    <header style=\"background-color: #333; text-align: center; padding: 1rem;\">\n" +
            "        <h1>Fictional Webpage</h1>\n" +
            "    </header>\n" +
            "\n" +
            "    <main style=\"text-align: center;\">\n" +
            "        <img src=\"https://picsum.photos/id/234/100/100\" alt=\"Sample Image\" style=\"max-width: 100%; height: auto; padding: 1rem; display: block; margin: 0 auto;\">\n" +
            "        <button onclick=\"callNative('native callbacks are cool!')\">Native callback!</button>" +
            "    </main>\n" +
            "\n" +
            "\n" +
            "</body>\n" +
            "</html>"

    private val iamSettings = InAppMessageSettings.Builder()
        .content(sampleHTML)
        .height(40)
        .width(70)
        .backgroundColor("#FF0000")
        .cornerRadius(10f)
        .displayAnimation(InAppMessageSettings.MessageAnimation.BOTTOM)
        .dismissAnimation(InAppMessageSettings.MessageAnimation.TOP)
        .gestureMap(
            mapOf(
                "swipeUp" to "https://adobe.com",
                "swipeDown" to "https://adobe.com",
                "swipeLeft" to "https://adobe.com",
                "swipeRight" to "https://google.com",
                "backgroundTap" to "https://google.com"
            )
        )

    private val iamEventListener = object : InAppMessageEventListener {
        override fun onBackPressed(message: Presentable<InAppMessage>) {
            Log.debug("UIServicesView", "InAppMessage", "onBackPressed")
        }

        override fun onUrlLoading(message: Presentable<InAppMessage>, url: String): Boolean {
            Log.debug("UIServicesView", "InAppMessage", "onUrlLoading")
            return false
        }

        override fun onShow(presentable: Presentable<InAppMessage>) {
            Log.debug("UIServicesView", "InAppMessage", "onShow")
            val message = presentable.getPresentation()
            message.eventHandler.handleJavascriptMessage("Android") {
                Log.debug("UIServicesView", "InAppMessage", "Message from InAppMessage: $it")
            }
        }

        override fun onHide(presentable: Presentable<InAppMessage>) {}

        override fun onDismiss(presentable: Presentable<InAppMessage>) {
        }

        override fun onError(presentable: Presentable<InAppMessage>, error: PresentationError) {
        }
    }

    fun create(): Presentable<InAppMessage> = ServiceProvider.getInstance().uiService.create(
        InAppMessage(iamSettings.build(), iamEventListener),
        presentationUtilityProvider
    )
}