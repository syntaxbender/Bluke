package dev.arnv.bluke.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeyboardAudioRoutingTest {
    @Test fun keyboardSoundsUseTheMediaStreamWhenTheRingerIsSilent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_SILENT
        val synthesizer = KeyboardSoundSynthesizer(context)
        try {
            val pool = ReflectionHelpers.getField<SoundPool>(synthesizer, "soundPool")
            val attributes = ReflectionHelpers.getField<AudioAttributes>(pool, "mAttributes")
            assertEquals(AudioAttributes.USAGE_MEDIA, attributes.usage)
            assertEquals(AudioManager.STREAM_MUSIC, attributes.volumeControlStream)
        } finally {
            synthesizer.release()
        }
    }
}
