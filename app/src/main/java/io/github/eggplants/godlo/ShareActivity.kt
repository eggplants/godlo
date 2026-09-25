package io.github.eggplants.godlo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.eggplants.godlo.core.SharedText

/**
 * Takes a link shared from a browser or another app to the download screen, then goes away.
 *
 * Not [MainActivity] itself: once a share had started it, Android took every later share for
 * the same intent, extras aside, and only brought the running app to the front with the old one.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The link is in the text, Chrome's with a quote before it when a passage was picked;
        // a few apps put it in the subject instead.
        val url = SharedText.url(intent?.getStringExtra(Intent.EXTRA_TEXT))
            ?: SharedText.url(intent?.getStringExtra(Intent.EXTRA_SUBJECT))
        if (url != null) {
            container.sharedUrl.value = url
            container.shares.tryEmit(Unit)
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
