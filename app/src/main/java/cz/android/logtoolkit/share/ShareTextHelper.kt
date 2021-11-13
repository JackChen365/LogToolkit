package cz.android.logtoolkit.share

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Parcelable
import java.util.ArrayList

object ShareTextHelper {
    private const val INTENT_TYPE_TEXT_PLAIN = "text/plain"

    fun createChooserAndShow(context: Context, sharedContent: CharSequence?) {
        createChooserAndShow(context, null, sharedContent)
    }

    fun createChooserAndShow(context: Context, sharedTitle: CharSequence?, sharedContent: CharSequence?) {
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sharedContent)
            type = INTENT_TYPE_TEXT_PLAIN
        }
        val shareIntents = getExternalShareIntents(context, shareIntent)
        val chooserIntent = Intent.createChooser(shareIntent, sharedTitle)
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, shareIntents.toArray(arrayOf<Parcelable>()))
        context.startActivity(chooserIntent)
    }

    @Throws(Throwable::class)
    private fun getExternalShareIntents(context: Context, shareIntent: Intent): ArrayList<Intent> {
        val resolveInfoList: List<ResolveInfo> = context.packageManager.queryIntentActivities(shareIntent, 0)
        val shareIntents: ArrayList<Intent> = ArrayList()
        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            val intent = Intent(shareIntent)
            intent.component = ComponentName(packageName, resolveInfo.activityInfo.name)
            shareIntents.add(intent)
        }
        return shareIntents
    }
}