package dev.erdos.automechanon

import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.MutableLiveData
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import kotlin.test.Test
import kotlin.test.assertEquals


@RunWith(MockitoJUnitRunner::class)
class BoxedUnitTest {

    @Test
    fun `lifecycle test`() {
        Mockito.mockStatic(Looper::class.java).use { utilities ->
            val looper = mockLooper()
            utilities.`when`<Any>(Looper::getMainLooper).thenReturn(looper)
            val edit = mockEdit()

            val mld = MutableLiveData(mapOf("x" to "initial"))
            val b = Lens.of(mld)

            b.reading { it["x"]!! }
                .writing { m, t -> m + ("x" to t) }
                .binding(edit)

            // THEN: listener is registered
            Mockito.verify(edit).addTextChangedListener(Mockito.any())

            // THEN: initial value is set properly
            Mockito.verify(edit).setTextKeepState("initial")

            // WHEN-THEN: modifying the original value propagates here
            mld.value = mapOf("x" to "zzz")
            Mockito.verify(edit).setTextKeepState("zzz")

            // WHEN-THEN: value set via another view (not the original)
            b.asMutableLiveData().value = mapOf("x" to "ddd")
            Mockito.verify(edit).setTextKeepState("ddd")
        }
    }

    @Test
    fun `lifecycle test without initial state`() {
        Mockito.mockStatic(Looper::class.java).use { utilities ->
            val looper = mockLooper()
            utilities.`when`<Any>(Looper::getMainLooper).thenReturn(looper)

            val mld = MutableLiveData(mapOf("a" to "b"))
            val b = Lens.of(mld)

            var result: String? = "initial"
            b.reading { it["x"] }
                .writing { m, t -> if (t == null) m - "x" else m + ("x" to t) }
                .asMutableLiveData()
                .observe(lifecycleOwner()) {
                    result = it
                }
            assertEquals(null, result)

            mld.value = mapOf("x" to "second")
            assertEquals("second", result)
        }
    }

    @Test
    fun `test setting same value twice`() {
        Mockito.mockStatic(Looper::class.java).use { utilities ->
            val looper = mockLooper()
            utilities.`when`<Any>(Looper::getMainLooper).thenReturn(looper)

            val mld = MutableLiveData(mapOf("a" to "b"))
            val b = Lens.of(mld)

            var result = mutableListOf<String?>()
            b.reading { it["x"] }
                .writing { m, t -> if (t == null) m - "x" else m + ("x" to t) }
                .asMutableLiveData()
                .observe(lifecycleOwner()) {
                    result.add(it)
                }
            assertEquals(listOf<String?>(null), result)

            mld.value = mapOf("x" to "one")
            assertEquals(listOf(null, "one"), result)

            mld.value = mapOf("x" to "one")
            assertEquals(listOf(null, "one"), result) // same value not set twice

            b.asMutableLiveData().value = mapOf("x" to "two")
            assertEquals(listOf(null, "one", "two"), result) // same value not set twice
        }
    }


    private fun lifecycleOwner(): LifecycleOwner {
        val lifecycle = LifecycleRegistry(Mockito.mock(LifecycleOwner::class.java))
        lifecycle.currentState = Lifecycle.State.RESUMED

        val ctx = Mockito.mock(AppCompatActivity::class.java)
        Mockito.`when`(ctx.lifecycle).thenReturn(lifecycle)

        return ctx
    }

    private fun mockEdit(): EditText {
        val lifecycle = LifecycleRegistry(Mockito.mock(LifecycleOwner::class.java))
        lifecycle.currentState = Lifecycle.State.RESUMED

        val ctx = Mockito.mock(AppCompatActivity::class.java)
        Mockito.`when`(ctx.lifecycle).thenReturn(lifecycle)

        val edit = Mockito.mock(EditText::class.java)
        Mockito.`when`(edit.context).thenReturn(ctx)
        return edit
    }

    private fun mockLooper(): Looper {
        val looper = Mockito.mock(Looper::class.java)
        Mockito.`when`(looper.thread).thenReturn(Thread.currentThread())
        return looper
    }
}

