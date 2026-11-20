package fr.paradoxxx.tester4k

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * Accès aux textes traduits (res/values = anglais par défaut, values-fr = français, values-ru = russe).
 */
object L {
    lateinit var res: Resources
        private set

    fun init(ctx: Context) {
        res = ctx.resources
    }

    fun s(@StringRes id: Int, vararg args: Any): String = res.getString(id, *args)
}
