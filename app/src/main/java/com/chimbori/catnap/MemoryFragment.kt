package com.chimbori.catnap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import androidx.fragment.app.ListFragment
import com.chimbori.catnap.MemoryArrayAdapter.Saved
import com.chimbori.catnap.TrackedPosition.Deleted
import com.mobeta.android.dslv.DragSortListView
import com.mobeta.android.dslv.DragSortListView.DropListener
import com.mobeta.android.dslv.DragSortListView.RemoveListener

class MemoryFragment : ListFragment(), OnItemClickListener, DropListener, RemoveListener {
  // This is basically the cached result of findScratchCopy().
  private val mScratchPos = TrackedPosition()
  private var mHeaderView: View? = null
  private var mDslv: DragSortListView? = null
  private var mUiState: UIState? = null
  private var mAdapter: MemoryArrayAdapter? = null
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    mDslv = inflater.inflate(
      R.layout.memory_list,
      container, false
    ) as DragSortListView
    val v = inflater.inflate(R.layout.memory_list_item_top, null)
    val button = v.findViewById<View>(R.id.save_button)
    button.setOnClickListener { // Clicked the "Save" button.
      val ph: Phonon = mUiState!!.mScratchPhonon!!.makeMutableCopy()
      mAdapter!!.insert(ph, 0)
      // Gray out the header row.
      setScratchPosAndDraw(findScratchCopy())
      // Fake-click the header row.
      onItemClick(null, null, 0, 0)
    }
    mHeaderView = v
    mDslv!!.addHeaderView(mHeaderView, null, true)
    mDslv!!.addHeaderView(
      inflater.inflate(R.layout.memory_list_divider, null), null,
      false
    )
    return mDslv
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    mUiState = (activity as MainActivity?)!!.uIState
    mAdapter = MemoryArrayAdapter(activity, mUiState!!.mSavedPhonons)
    setListAdapter(mAdapter)
    mDslv!!.onItemClickListener = this
    mDslv!!.setDropListener(this)
    mDslv!!.setRemoveListener(this)
  }

  override fun onResume() {
    super.onResume()
    (activity as MainActivity?)!!.setFragmentId(FragmentIndex.ID_MEMORY)
    setScratchPosAndDraw(findScratchCopy())
    syncActiveItem(false)
  }

  override fun drop(from: Int, to: Int) {
    if (from != to) {
      val item = mAdapter!!.getItem(from)
      mAdapter!!.remove(item)
      mAdapter!!.insert(item, to)
      moveTrackedPositions(from, to, null)
    }
  }

  override fun remove(which: Int) {
    val item = mAdapter!!.getItem(which)
    mAdapter!!.remove(item)
    moveTrackedPositions(which, TrackedPosition.NOWHERE, item)
  }

  private fun moveTrackedPositions(from: Int, to: Int, deleted: Phonon?) {
    require(to == TrackedPosition.NOWHERE == (deleted != null))
    try {
      if (mUiState!!.mActivePos.move(from, to)) {
        // Move the radio button.
        syncActiveItem(false)
      }
    } catch (e: Deleted) {
      // The active item was deleted!
      // Move it to scratch, so it can keep playing.
      mUiState!!.mScratchPhonon = deleted!!.makeMutableCopy()
      setScratchPosAndDraw(-1)
      mUiState!!.mActivePos.pos = -1
      syncActiveItem(true)
      return
    }
    try {
      mScratchPos.move(from, to)
    } catch (e: Deleted) {
      // The (inactive) scratch copy was deleted!
      // Reactivate the real scratch.
      setScratchPosAndDraw(-1)
    }
  }

  override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
    mUiState!!.setActivePhonon(if (position == 0) -1 else position - mDslv!!.headerViewsCount)
    if (position == 0) {
      // User clicked on the scratch.  Jump to a copy if one exists.
      syncActiveItem(true)
    }
  }

  // Header row should be grayed out whenever the scratch is redundant.
  private fun setScratchPosAndDraw(pos: Int) {
    mScratchPos.pos = pos
    val enabled = pos == -1
    mHeaderView!!.setEnabled(enabled)
    mAdapter!!.initListItem(
      mHeaderView!!, mUiState!!.mScratchPhonon, if (enabled) Saved.NO else Saved.YES
    )
  }

  // If the scratch Phonon is unique, return -1.  Otherwise, return the
  // copy's index within mArray.
  private fun findScratchCopy(): Int {
    val scratch = mUiState!!.mScratchPhonon
    for (i in mUiState!!.mSavedPhonons!!.indices) {
      if (mUiState!!.mSavedPhonons!![i].fastEquals(scratch)) {
        return i
      }
    }
    return -1
  }

  private fun syncActiveItem(scrollThere: Boolean) {
    // Determine which index to check.
    var index = mUiState!!.mActivePos.pos
    if (index == -1) {
      index = mScratchPos.pos
      mUiState!!.mActivePos.pos = index
    }

    // Convert the index to a list row.
    if (index == -1) {
      index = 0
    } else {
      index += mDslv!!.headerViewsCount
    }

    // Modify the UI.
    mDslv!!.setItemChecked(index, true)
    if (scrollThere) {
      mDslv!!.smoothScrollToPosition(index)
    }
  }
}
