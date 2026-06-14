package org.schabi.newpipe.error

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize
import org.schabi.newpipe.R

/**
 * Categorizes the source of a playback failure so the user can understand
 * what went wrong at a glance (e.g. in a notification).
 *
 * This enum intentionally does not depend on any extractor types — the
 * extractor library is an external dependency and must not be changed.
 */
@Parcelize
enum class PlaybackErrorSource(@StringRes val labelRes: Int) : Parcelable {
    /** The media stream could not be parsed or resolved (extraction, format, manifest). */
    MEDIA_PARSING(R.string.error_source_media_parsing),

    /** A network-level failure prevented playback (timeout, DNS, HTTP error). */
    NETWORK(R.string.error_source_network),

    /** The player engine itself failed to initialize or render (decoder, renderer). */
    PLAYER_INITIALIZATION(R.string.error_source_player_initialization);
}
