package app.olauncher.ui

import android.content.Context
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.AppSelectorActivity
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.databinding.AppSelectorAdapterBinding
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import java.text.Normalizer

class AppSelectorAdapter(
    private var activity: AppSelectorActivity,
    private val appLabelGravity: Int,
) : ListAdapter<AppModel, AppSelectorAdapterViewHolder>(AppSelectorAdapterDiff()), Filterable {

    private var autoLaunch = true
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppSelectorAdapterViewHolder {
        return AppSelectorAdapterViewHolder(
            AppSelectorAdapterBinding.inflate(
                LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: AppSelectorAdapterViewHolder, position: Int) {
        try {
            if (appFilteredList.size == 0 || position == RecyclerView.NO_POSITION) return
            val appModel = appFilteredList[holder.bindingAdapterPosition]
            holder.bind(
                activity,
                appLabelGravity,
                myUserHandle,
                appModel,
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                autoLaunch = charSearch?.startsWith(" ")?.not() ?: true

                val appFilteredList = (if (charSearch.isNullOrBlank()) appsList
                else appsList.filter { app ->
                    appLabelMatches(app.appLabel, charSearch)
//                }.sortedByDescending {
//                    charSearch.contentEquals(it.appLabel, true)
                } as MutableList<AppModel>)

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }


            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    val items = it as MutableList<AppModel>
                    appFilteredList = items
                    submitList(appFilteredList) {
                        autoLaunch()
                    }
                }
            }
        }
    }

    private fun autoLaunch() {
        try {
            if (itemCount == 1
                && autoLaunch
                && appFilteredList.size > 0
            ) activity.returnResults(appFilteredList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun appLabelMatches(appLabel: String, charSearch: CharSequence): Boolean {
        return (appLabel.contains(charSearch.trim(), true) or
                Normalizer.normalize(appLabel, Normalizer.Form.NFD)
                    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                    .replace(Regex("[-_+,. ]"), "")
                    .contains(charSearch, true))
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        // Add empty app for bottom padding in recyclerview
        appsList.add(AppModel("", null, "", "", false, android.os.Process.myUserHandle()))
        this.appsList = appsList
        this.appFilteredList = appsList
        submitList(appsList)
    }

    fun launchFirstInList() {
        if (appFilteredList.size > 0)
            activity.returnResults(appFilteredList[0])
    }
}

class AppSelectorAdapterViewHolder(private val binding: AppSelectorAdapterBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(
        activity: AppSelectorActivity,
        appLabelGravity: Int,
        myUserHandle: UserHandle,
        appModel: AppModel,
    ) =
        with(binding) {
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE
            appTitle.text = appModel.appLabel + if (appModel.isNew == true) " ✨" else ""
            appTitle.gravity = appLabelGravity
            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            appTitle.setOnClickListener { activity.returnResults(appModel) }

            appTitle.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    appDelete.alpha =
                        if (root.context.isSystemApp(appModel.appPackage)) 0.5f else 1.0f
                    appHide.text = if (activity.showHiddenApps)
                        root.context.getString(R.string.adapter_show)
                    else
                        root.context.getString(R.string.adapter_hide)
                    appTitle.visibility = View.INVISIBLE
                    appHideLayout.visibility = View.VISIBLE
                    appRename.isVisible = activity.canRename
                }
                true
            }

            appRename.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.visibility = View.VISIBLE
                    appHideLayout.visibility = View.GONE
                    etAppRename.showKeyboard()
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }

            etAppRename.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus)
                    appTitle.visibility = View.INVISIBLE
                else
                    appTitle.visibility = View.VISIBLE
            }

            etAppRename.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage)
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    etAppRename.hint = ""
                }
            })

            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        activity.prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                        activity.viewModel.getAppList()
                        renameLayout.visibility = View.GONE
                    }
                    true
                }
                false
            }

            tvSaveRename.setOnClickListener {
                etAppRename.hideKeyboard()
                if (appModel.appPackage.isNotBlank()) {
                    var renameLabel = etAppRename.text.toString().trim()

                    if (renameLabel.isBlank()) {
                        val packageManager = etAppRename.context.packageManager
                        renameLabel = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(appModel.appPackage, 0)
                        ).toString()
                    }

                    activity.prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                    activity.viewModel.getAppList()
                    renameLayout.visibility = View.GONE
                }
            }

            appInfo.setOnClickListener {
                openAppInfo(
                    activity,
                    appModel.user,
                    appModel.appPackage
                )
            }
            appDelete.setOnClickListener {
                activity.apply {
                    if (isSystemApp(appModel.appPackage))
                        showToast(getString(R.string.system_app_cannot_delete))
                    else
                        uninstall(appModel.appPackage)
                }

            }
            appMenuClose.setOnClickListener {
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appRenameClose.setOnClickListener {
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appHide.setOnClickListener {
                activity.adapter.appFilteredList.removeAt(position)
                activity.adapter.notifyItemRemoved(position)
                activity.adapter.appsList.remove(appModel)

                val newSet = mutableSetOf<String>()
                newSet.addAll(activity.prefs.hiddenApps)
                if (activity.showHiddenApps) {
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                activity.prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    activity.returnResults(null)
                activity.viewModel.getAppList()
                activity.viewModel.getHiddenApps()
            }
        }

    private fun getAppName(context: Context, appPackage: String): String {
        val packageManager = context.packageManager
        return packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(appPackage, 0)
        ).toString()
    }
}

private class AppSelectorAdapterDiff : DiffUtil.ItemCallback<AppModel>() {
    override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
        return oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user
    }

    override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
        return oldItem == newItem
    }

}