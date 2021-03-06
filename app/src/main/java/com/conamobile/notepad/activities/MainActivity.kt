package com.conamobile.notepad.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.conamobile.notepad.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.conamobile.notepad.adapter.NoteAdapter
import com.conamobile.notepad.click.ItemClickListener
import com.conamobile.notepad.database.RoomDB
import com.conamobile.notepad.memory.SharedManager
import com.conamobile.notepad.model.Notes
import com.crowdfire.cfalertdialog.CFAlertDialog

class MainActivity : AppCompatActivity(), PopupMenu.OnMenuItemClickListener {
    private var recyclerView: RecyclerView? = null
    private var noteListAdapter: NoteAdapter? = null
    private var notes: ArrayList<Notes> = ArrayList()
    private lateinit var database: RoomDB
    private lateinit var searchView: EditText
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var swipe: SwipeRefreshLayout
    private var selectedNote: Notes? = null
    private lateinit var view: View
    private lateinit var linearLayout: LinearLayout
    private lateinit var sharedManager: SharedManager
    private lateinit var mAdView: AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.sleep(1000)
        val splashScreen = installSplashScreen()
        setContentView(R.layout.activity_main)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        initViews()

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initViews() {
        sharedManager = SharedManager(this)
        linearLayout = findViewById(R.id.linear_gone)
        recyclerView = findViewById(R.id.recycler_home)
        fabAdd = findViewById(R.id.fab_add)
        swipe = findViewById(R.id.swipe_refresh)
        searchView = findViewById(R.id.search_view)

        actionBarTitle()

        swipe.setOnRefreshListener {
            noteListAdapter!!.notifyDataSetChanged()
            swipe.isRefreshing = false
        }
        view = swipe

        database = RoomDB.getInstance(this)
        notes = database.mainDao().getAll() as ArrayList<Notes>

        updateRecyclerView(notes)

        fabAdd.setOnClickListener {
            val intent = Intent(this, NotesTakerActivity::class.java)
            startActivityForResult(intent, 101)
        }

        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {
                filter(p0)
            }

            override fun afterTextChanged(p0: Editable?) {
            }
        })
        isCheck()

        admob()
    }

    private fun actionBarTitle() {
        if (supportActionBar != null)
            supportActionBar!!.title = getString(R.string.notes)
    }

    private fun admob() {
        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    private fun filter(newText: CharSequence) {
        val filteredList: ArrayList<Notes> = ArrayList()
        for (singleNote in notes) {
            if (singleNote.title.lowercase().contains(newText.toString().lowercase())
                || singleNote.note.lowercase().contains(newText.toString().lowercase())
            ) {
                filteredList.add(singleNote)
            }
        }
        noteListAdapter!!.filterList(filteredList)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            if (resultCode == Activity.RESULT_OK) {
                val newNotes = data!!.getSerializableExtra("note") as Notes
                database.mainDao().insert(newNotes)
                notes.clear()
                updateRecyclerView(notes)
                notes.addAll(database.mainDao().getAll())
                noteListAdapter!!.notifyDataSetChanged()
                isCheck()
            }
        } else if (requestCode == 102) {
            if (resultCode == Activity.RESULT_OK) {
                val newNote = data!!.getSerializableExtra("note") as Notes
                database.mainDao().update(newNote.ID, newNote.title, newNote.note)
                notes.clear()
                updateRecyclerView(notes)
                notes.addAll(database.mainDao().getAll())
                noteListAdapter!!.notifyDataSetChanged()
                isCheck()
            }
        }
    }

    private fun updateRecyclerView(notes: ArrayList<Notes>) {
        recyclerView!!.apply {
            layoutManager = if (!sharedManager.getSavedManager()) {
                StaggeredGridLayoutManager(2, LinearLayoutManager.VERTICAL)
            } else {
                LinearLayoutManager(this@MainActivity)
            }
            noteListAdapter = NoteAdapter(this@MainActivity, notes, notesClickListener)
            adapter = noteListAdapter
        }
    }

    private val notesClickListener = object : ItemClickListener {
        override fun onClick(notes: Notes) {
            val intent = Intent(this@MainActivity, NotesTakerActivity::class.java)
            intent.putExtra("old_note", notes)
            startActivityForResult(intent, 102)
            isCheck()
        }

        override fun onLongClick(notes: Notes, cardView: CardView) {
            selectedNote = Notes()
            selectedNote = notes
            showPopup(cardView)
            isCheck()
        }
    }

    private fun showPopup(cardView: CardView) {
        val popupMenu = PopupMenu(this, cardView)
        popupMenu.setOnMenuItemClickListener(this)
        popupMenu.inflate(R.menu.popup_menu)
        popupMenu.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onMenuItemClick(menuItem: MenuItem?): Boolean {
        when (menuItem!!.itemId) {
            R.id.pin_p -> {
                if (selectedNote!!.pinned) {
                    database.mainDao().pin(selectedNote!!.ID, false)
                    snackBar("Unpinned")
                } else {
                    database.mainDao().pin(selectedNote!!.ID, true)
                    snackBar("Pinned")
                }
                notes.clear()
                notes.addAll(database.mainDao().getAll())
                noteListAdapter!!.notifyDataSetChanged()
                return true
            }
            R.id.delete -> {
                database.mainDao().delete(selectedNote!!)
                notes.remove(selectedNote)
                noteListAdapter?.notifyDataSetChanged()
                snackBar("Note deleted!")
                isCheck()
                return true
            }
        }
        return false
    }

    private fun isCheck() {
        if (notes.isEmpty()) {
            linearLayout.visibility = View.VISIBLE
        } else {
            linearLayout.visibility = View.GONE
        }
    }

    private fun snackBar(str: String?) {
        val snackBar = Snackbar.make(view, str!!, Snackbar.LENGTH_SHORT)
        snackBar.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.option_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.list_1 -> {
                recyclerView!!.layoutManager = LinearLayoutManager(this)
                sharedManager.isSavedManager(true)
            }
            R.id.grid_2 -> {
                recyclerView!!.layoutManager =
                    StaggeredGridLayoutManager(2, LinearLayoutManager.VERTICAL)
                sharedManager.isSavedManager(false)
            }
            R.id.delete_all -> {
                if (notes.isNotEmpty()) {
                    showAlertDialog()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun showAlertDialog() {
        val alertDialog = CFAlertDialog.Builder(this)
            .setDialogStyle(CFAlertDialog.CFAlertStyle.ALERT)
            .setTitle(getString(R.string.are_you))
            .addButton(
                getString(R.string.yes),
                -1,
                -1,
                CFAlertDialog.CFAlertActionStyle.NEGATIVE,
                CFAlertDialog.CFAlertActionAlignment.JUSTIFIED
            ) { dialog, _ ->
                database.mainDao().deleteAllData()
                notes.clear()
                updateRecyclerView(notes)
                snackBar("Deleted all data")
                isCheck()
                dialog.dismiss()
            }
            .addButton(
                getString(R.string.yoq),
                -1,
                -1,
                CFAlertDialog.CFAlertActionStyle.POSITIVE,
                CFAlertDialog.CFAlertActionAlignment.JUSTIFIED
            ) { dialog, _ ->
                dialog.dismiss()
            }
        alertDialog.show()
    }
}