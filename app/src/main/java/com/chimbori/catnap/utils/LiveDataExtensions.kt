package com.chimbori.catnap.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

val <T> LiveData<T>.nonNullValue
  get() = value!!

/**
 * When using a ViewModel purely for message passing, this convenience method can reduce boilerplate & improve readability.
 * Don’t use it from outside the [ViewModel] class: [LiveData] should not be accessed as [MutableLiveData] outside the [ViewModel].
 * This force-casts the [LiveData] to a [MutableLiveData] so that if it’s the wrong type, we crash early instead of failing silently.
 */
fun <T> LiveData<T>.update(newValue: T? = null) {
  (this as MutableLiveData<T>).value = newValue
}
