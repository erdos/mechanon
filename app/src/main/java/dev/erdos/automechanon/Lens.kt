package dev.erdos.automechanon

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.map

typealias Reducer<S, T> = (S, T) -> S

interface Lens<S> {
    //fun <V> reading(m: (S) -> V): ReadOnlyBoxed<Any, V>

    fun <V> readAndWrite(mapper: (S) -> V, unmapper: Reducer<S, V>): Lens<V>
    fun asMutableLiveData(): MutableLiveData<S>

    fun <V> reading(mapper: (S) -> V): MappedLensStub<S, V> = MappedLensStub(this, mapper)

    companion object {
        fun <T> of(data: MutableLiveData<T>): Lens<T> = LensImpl(data, { it }, { _, t -> t })
    }

    @Composable
    fun observableAsState() = reading({ it }).asLiveData().observeAsState() as State<S>
}

fun <S> MutableLiveData<S>.update(mapper: (S) -> S): MutableLiveData<S> {
    this.value = mapper(this.value!!)
    return this
}

class MappedLensStub<S, V>(private val parent: Lens<S>, private val mapper: (S) -> V) {
    fun asLiveData(): LiveData<V> = parent.asMutableLiveData().map(mapper).distinctUntilChanged()
    fun <U> reading(m2: (V) -> U) = MappedLensStub(parent) { m2(mapper(it)) }
    fun writing(reducer: Reducer<S, V>) = parent.readAndWrite(mapper, reducer)
}

private class LensImpl<S, T>(
    private val source: MutableLiveData<S>,
    private val mapper: (S) -> T,
    private val reducer: Reducer<S, T>): Lens<T> {
    override fun <V> readAndWrite(m: (T) -> V, r: Reducer<T, V>): Lens<V> =
        LensImpl(source, { m(mapper(it)) }, { m, x -> reducer(m, r(mapper(m), x)) })

    override fun asMutableLiveData() = MediatorLiveData<T>().apply {
        val mappedSource = source.distinctUntilChanged().map(mapper)
        value = mappedSource.value
        addSource(mappedSource) {
            value = it
        }
        observeForever {
            source.value = reducer(source.value!!, it)
        }
    }
}

private fun <T> Lens<T>.callbacks(lifecycle: LifecycleOwner, onchange: (T) -> Unit): (T) -> Unit {
    val mld = asMutableLiveData()
    mld.distinctUntilChanged().observe(lifecycle, onchange)
    return { it -> mld.value = it }
}

fun <T, V: View> MappedLensStub<Any, T?>.bindVisibility(view: V): V = view.apply {
    asLiveData().observe(findViewTreeLifecycleOwner()!!) {
        isVisible = (it != null)
    }
}

fun Lens<String>.binding(edit: EditText) : EditText {
    val callback: (String) -> Unit = callbacks(edit.context as LifecycleOwner) {
        edit.setTextKeepState(it)
    }
    edit.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            callback(s!!.toString())
        }
    })
    return edit
}

fun testing() {
    val mld = MutableLiveData(mapOf("x" to "y"))
    val b = Lens.of(mld)

    b.reading { it["x"]!! }
        .writing { m, t -> m + ("x" to t) }
        .binding(EditText(null))
}