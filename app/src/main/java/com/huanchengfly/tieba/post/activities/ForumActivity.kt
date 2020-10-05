@file:Suppress("DEPRECATION")

package com.huanchengfly.tieba.post.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.Fragment
import butterknife.BindView
import cn.jzvd.Jzvd
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.huanchengfly.tieba.post.R
import com.huanchengfly.tieba.post.adapters.FragmentTabViewPagerAdapter
import com.huanchengfly.tieba.post.adapters.SingleChooseAdapter
import com.huanchengfly.tieba.post.api.ForumSortType
import com.huanchengfly.tieba.post.api.TiebaApi
import com.huanchengfly.tieba.post.api.models.CommonResponse
import com.huanchengfly.tieba.post.api.models.ForumPageBean
import com.huanchengfly.tieba.post.api.models.LikeForumResultBean
import com.huanchengfly.tieba.post.api.models.SignResultBean
import com.huanchengfly.tieba.post.fragments.ForumFragment
import com.huanchengfly.tieba.post.fragments.ForumFragment.OnRefreshedListener
import com.huanchengfly.tieba.post.goToActivity
import com.huanchengfly.tieba.post.interfaces.Refreshable
import com.huanchengfly.tieba.post.interfaces.ScrollTopable
import com.huanchengfly.tieba.post.models.PhotoViewBean
import com.huanchengfly.tieba.post.models.database.History
import com.huanchengfly.tieba.post.ui.theme.utils.ThemeUtils
import com.huanchengfly.tieba.post.utils.*
import com.huanchengfly.tieba.post.utils.ColorUtils.getDarkerColor
import com.huanchengfly.tieba.post.utils.ColorUtils.greifyColor
import com.huanchengfly.tieba.post.utils.anim.animSet
import com.huanchengfly.tieba.post.utils.preload.PreloadUtil
import com.huanchengfly.tieba.post.widgets.MyViewPager
import com.huanchengfly.tieba.post.widgets.theme.TintProgressBar
import com.huanchengfly.tieba.post.widgets.theme.TintToolbar
import com.lapism.searchview.widget.SearchView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.math.abs

class ForumActivity : BaseActivity(), View.OnClickListener, OnRefreshedListener, TabLayout.OnTabSelectedListener {
    private var mSortType = ForumSortType.REPLY_TIME
    private var forumName: String? = null
    private var firstLoaded = false
    private var animated = false

    @BindView(R.id.toolbar)
    lateinit var toolbar: TintToolbar

    @BindView(R.id.forum_view_pager)
    lateinit var myViewPager: MyViewPager
    private var mAdapter: FragmentTabViewPagerAdapter? = null
    private var mDataBean: ForumPageBean? = null

    @BindView(R.id.fab)
    lateinit var fab: FloatingActionButton
    private var historyHelper: HistoryHelper? = null

    @BindView(R.id.toolbar_search_view)
    lateinit var searchView: SearchView

    @BindView(R.id.loading_view)
    lateinit var loadingView: View

    @BindView(R.id.toolbar_btn_right)
    lateinit var toolbarEndBtn: MaterialButton

    @BindView(R.id.header_view_parent)
    lateinit var headerView: View

    @BindView(R.id.forum_header_name)
    lateinit var headerNameTextView: TextView

    @BindView(R.id.forum_header_slogan)
    lateinit var headerSloganTextView: TextView

    @BindView(R.id.forum_header_stat_members)
    lateinit var statMembersTextView: TextView

    @BindView(R.id.forum_header_stat_posts)
    lateinit var statPostsTextView: TextView

    @BindView(R.id.forum_header_stat_threads)
    lateinit var statThreadsTextView: TextView

    @BindView(R.id.forum_header_tip)
    lateinit var tipTextView: TextView

    @BindView(R.id.forum_header_avatar)
    lateinit var avatarView: ImageView

    @BindView(R.id.forum_header_button)
    lateinit var button: MaterialButton

    @BindView(R.id.forum_tab)
    lateinit var headerTabView: TabLayout

    @BindView(R.id.forum_header_progress)
    lateinit var progressBar: ProgressBar

    @BindView(R.id.appbar)
    lateinit var appbar: AppBarLayout

    @BindView(R.id.collapsing_toolbar)
    lateinit var collapsingToolbar: CollapsingToolbarLayout

    var toolbarColor: Int = Color.TRANSPARENT

    override fun getLayoutId(): Int {
        return R.layout.activity_forum
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUtil.setTranslucentThemeBackground(findViewById(R.id.background))
        toolbarColor = ThemeUtils.getColorById(this, R.color.default_color_toolbar)
        historyHelper = HistoryHelper(this)
        animated = false
        val intent = intent
        val title: String
        if (intent.getBooleanExtra("jumpByUrl", false)) {
            val url = intent.getStringExtra("url")
            val uri = Uri.parse(url)
            forumName = uri.getQueryParameter("kw")
            title = getString(R.string.title_forum, forumName)
        } else {
            forumName = intent.getStringExtra(EXTRA_FORUM_NAME)
            title = getString(R.string.title_forum, forumName)
        }
        if (forumName == null) {
            finish()
            return
        }
        initView()
        setTitle(title)
        initData()
    }

    private fun getSortType(): ForumSortType {
        val defaultSortType = appPreferences.defaultSortType!!.toInt()
        return ForumSortType.valueOf(SharedPreferencesUtil.get(this, SharedPreferencesUtil.SP_SETTINGS)
                .getInt(forumName + "_sort_type", defaultSortType))
    }

    private fun setSortType(sortType: ForumSortType) {
        this.mSortType = sortType
        for (fragment in mAdapter!!.fragments) {
            if (fragment is ForumFragment) {
                fragment.setSortType(sortType)
            }
        }
        refresh()
        SharedPreferencesUtil.get(this, SharedPreferencesUtil.SP_SETTINGS)
                .edit()
                .putInt(forumName + "_sort_type", sortType.value)
                .apply()
    }

    private fun refresh() {
        refreshHeaderView()
        if (currentFragment is Refreshable) {
            (currentFragment as Refreshable).onRefresh()
        }
    }

    private fun initData() {
        firstLoaded = true
        mSortType = getSortType()
        /*
        if (baName != null) {
            refresh();
        }
        */
    }

    private fun initView() {
        appbar.addOnOffsetChangedListener(OnOffsetChangedListener { _, verticalOffset: Int ->
            val titleVisible = mDataBean != null && forumName != null && abs(verticalOffset) >= headerView.height
            val percent: Float = if (abs(verticalOffset) <= headerView.height) {
                abs(verticalOffset.toFloat()) / headerView.height.toFloat()
            } else {
                1f
            }
            title = if (titleVisible) getString(R.string.title_forum, forumName) else null
            toolbarEndBtn.visibility = if (titleVisible) View.VISIBLE else View.GONE
            toolbar.backgroundTintList = ColorStateList.valueOf(Util.changeAlpha(toolbarColor, percent))
            if (animated && ThemeUtil.THEME_TRANSLUCENT == ThemeUtil.getTheme(this)) {
                if (abs(verticalOffset) > headerView.height) {
                    AnimUtil.alphaOut(headerView).setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            headerView.visibility = View.INVISIBLE
                        }
                    }).start()
                } else {
                    AnimUtil.alphaIn(headerView).start()
                }
            }
        })
        mAdapter = FragmentTabViewPagerAdapter(supportFragmentManager).apply {
            addFragment(
                    if (PreloadUtil.isPreloading(this@ForumActivity))
                        ForumFragment.newInstance(forumName, false, getSortType(), PreloadUtil.getPreloadId(this@ForumActivity))
                    else
                        ForumFragment.newInstance(forumName, false, getSortType()),
                    getString(R.string.tab_forum_1)
            )
            addFragment(ForumFragment.newInstance(forumName, true, getSortType()), getString(R.string.tab_forum_good))
        }
        myViewPager.apply {
            adapter = mAdapter
            offscreenPageLimit = 1
            setCurrentItem(0, false)
        }
        headerTabView.apply {
            setupWithViewPager(myViewPager)
            addOnTabSelectedListener(this@ForumActivity)
            for (i in 0 until mAdapter!!.fragments.size) {
                getTabAt(i)!!.setCustomView(R.layout.layout_tab_arrow)
                val arrow = getTabAt(i)!!.customView!!.findViewById<ImageView>(R.id.arrow)
                arrow.rotation = 180f
                arrow.visibility = if (getTabAt(i)!!.isSelected) View.VISIBLE else View.GONE
            }
        }
        refreshHeaderView()
        fab.hide()
        fab.supportImageTintList = ColorStateList.valueOf(resources.getColor(R.color.white))
        searchView.setHint(getString(R.string.hint_search_in_ba, forumName))
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        button.setOnClickListener(this)
        toolbar.setOnClickListener(this)
        toolbarEndBtn.setOnClickListener(this)
        fab.setOnClickListener(this)
    }

    override fun setTitle(newTitle: String) {
        toolbar.title = newTitle
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_unfollow -> {
                if (mDataBean != null) {
                    DialogUtil.build(this@ForumActivity)
                            .setTitle(R.string.title_dialog_unfollow)
                            .setNegativeButton(R.string.button_cancel, null)
                            .setPositiveButton(R.string.button_sure_default) { _, _ ->
                                TiebaApi.getInstance().unlikeForum(mDataBean!!.forum?.id!!, mDataBean!!.forum?.name!!, mDataBean!!.anti?.tbs!!).enqueue(object : Callback<CommonResponse> {
                                    override fun onFailure(call: Call<CommonResponse>, t: Throwable) {
                                        Util.createSnackbar(myViewPager, getString(R.string.toast_unlike_failed, t.message), Snackbar.LENGTH_SHORT).show()
                                    }

                                    override fun onResponse(call: Call<CommonResponse>, response: Response<CommonResponse>) {
                                        Util.createSnackbar(myViewPager, R.string.toast_unlike_success, Snackbar.LENGTH_SHORT).show()
                                        refresh()
                                    }
                                })
                            }
                            .create()
                            .show()
                }
            }
            R.id.menu_share -> TiebaUtil.shareText(this, "https://tieba.baidu.com/f?kw=$forumName", getString(R.string.title_forum, forumName))
            R.id.menu_search -> startActivity(Intent(this, SearchPostActivity::class.java).putExtra(SearchPostActivity.PARAM_FORUM, forumName))
            R.id.menu_refresh -> refresh()
            R.id.menu_send_to_desktop -> if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                if (mDataBean != null) {
                    Glide.with(this)
                            .asBitmap()
                            .apply(RequestOptions.circleCropTransform())
                            .load(mDataBean!!.forum?.avatar)
                            .into(object : SimpleTarget<Bitmap>() {
                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    val shortcutInfoIntent = Intent(this@ForumActivity, ForumActivity::class.java)
                                            .setAction(Intent.ACTION_VIEW)
                                            .putExtra(EXTRA_FORUM_NAME, mDataBean!!.forum?.name)
                                    val shortcutInfoCompat = ShortcutInfoCompat.Builder(this@ForumActivity, mDataBean!!.forum?.id!!)
                                            .setIntent(shortcutInfoIntent)
                                            .setShortLabel(mDataBean!!.forum?.name + "吧")
                                            .setIcon(IconCompat.createWithBitmap(resource))
                                            .build()
                                    ShortcutManagerCompat.requestPinShortcut(this@ForumActivity, shortcutInfoCompat, null)
                                    Util.createSnackbar(myViewPager, R.string.toast_send_to_desktop_success, Snackbar.LENGTH_SHORT).show()
                                }
                            })
                } else {
                    Util.createSnackbar(myViewPager, getString(R.string.toast_send_to_desktop_failed, "获取吧信息失败"), Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Util.createSnackbar(myViewPager, getString(R.string.toast_send_to_desktop_failed, "启动器不支持创建快捷方式"), Snackbar.LENGTH_SHORT).show()
            }
            R.id.menu_exit -> finish()
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ba_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.fab -> {
                if (mDataBean == null) {
                    return
                }
                if ("0" != mDataBean!!.anti?.ifPost) {
                    NavigationHelper.newInstance(this).navigationByData(NavigationHelper.ACTION_THREAD_POST, forumName)
                } else {
                    if (!TextUtils.isEmpty(mDataBean!!.anti?.forbidInfo)) {
                        Toast.makeText(this, mDataBean!!.anti?.forbidInfo, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            R.id.toolbar -> scrollToTop()
            R.id.forum_header_button, R.id.toolbar_btn_right -> if (mDataBean != null) {
                if ("1" == mDataBean!!.forum?.isLike) {
                    if ("0" == mDataBean!!.forum?.signInInfo?.userInfo?.isSignIn) {
                        TiebaApi.getInstance().sign(mDataBean!!.forum?.name!!, mDataBean!!.anti?.tbs!!).enqueue(object : Callback<SignResultBean> {
                            override fun onFailure(call: Call<SignResultBean>, t: Throwable) {
                                Util.createSnackbar(myViewPager, getString(R.string.toast_sign_failed, t.message), Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onResponse(call: Call<SignResultBean>, response: Response<SignResultBean>) {
                                val signResultBean = response.body()!!
                                if (signResultBean.userInfo != null) {
                                    mDataBean!!.forum?.signInInfo?.userInfo?.isSignIn = "1"
                                    Util.createSnackbar(myViewPager, getString(R.string.toast_sign_success, signResultBean.userInfo.signBonusPoint, signResultBean.userInfo.userSignRank), Snackbar.LENGTH_SHORT).show()
                                    refreshHeaderView()
                                    refreshForumInfo()
                                }
                            }
                        })
                    }
                } else {
                    TiebaApi.getInstance().likeForum(mDataBean!!.forum?.id!!, mDataBean!!.forum?.name!!, mDataBean!!.anti?.tbs!!).enqueue(object : Callback<LikeForumResultBean> {
                        override fun onFailure(call: Call<LikeForumResultBean>, t: Throwable) {
                            Toast.makeText(this@ForumActivity, getString(R.string.toast_like_failed, t.message), Toast.LENGTH_SHORT).show()
                        }

                        override fun onResponse(call: Call<LikeForumResultBean>, response: Response<LikeForumResultBean>) {
                            mDataBean!!.forum?.isLike = "1"
                            Toast.makeText(this@ForumActivity, getString(R.string.toast_like_success, response.body()!!.info?.memberSum), Toast.LENGTH_SHORT).show()
                            refreshHeaderView()
                            refreshForumInfo()
                        }
                    })
                }
            }
        }
    }

    private fun getNumStr(num: String): String {
        val long = num.toLong()
        if (long > 9999) {
            val longW = long / 10000L
            if (longW > 999) {
                val longKW = longW / 1000L
                return "${longKW}KW"
            } else {
                return "${longW}W"
            }
        } else {
            return num
        }
    }

    private fun refreshHeaderView() {
        if (mDataBean != null && mDataBean!!.forum != null) {
            headerView.visibility = View.VISIBLE
            val color = getDarkerColor(greifyColor(Color.parseColor("#${mDataBean!!.forum!!.themeColor.day.commonColor}"), 0.15f), 0.1f)
            toolbarColor = color
            appbar.backgroundTintList = ColorStateList.valueOf(color)
            setCustomToolbarColor(getDarkerColor(color, 0.1f))
            if (avatarView.tag == null) {
                ImageUtil.load(avatarView, ImageUtil.LOAD_TYPE_AVATAR, mDataBean!!.forum!!.avatar)
                ImageUtil.initImageView(avatarView, PhotoViewBean(mDataBean!!.forum!!.avatar, false))
            }
            (progressBar as TintProgressBar?)!!.setProgressBackgroundTintResId(if (ThemeUtils.getColorByAttr(this, R.attr.colorToolbar) == ThemeUtils.getColorByAttr(this, R.attr.colorBg)) R.color.default_color_divider else R.color.default_color_toolbar_item_secondary)
            progressBar.visibility = if ("1" == mDataBean!!.forum?.isLike) View.VISIBLE else View.GONE
            try {
                progressBar.max = Integer.valueOf(mDataBean!!.forum?.levelUpScore!!)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress(Integer.valueOf(mDataBean!!.forum?.curScore!!), true)
                } else {
                    progressBar.progress = Integer.valueOf(mDataBean!!.forum?.curScore!!)
                }
            } catch (ignored: Exception) {
            }
            listOf(
                    statMembersTextView,
                    statPostsTextView,
                    statThreadsTextView
            ).forEach {
                it.typeface = Typeface.createFromAsset(assets, "bebas.ttf")
            }
            statMembersTextView.text = getNumStr(mDataBean!!.forum!!.memberNum!!)
            statPostsTextView.text = getNumStr(mDataBean!!.forum!!.postNum!!)
            statThreadsTextView.text = getNumStr(mDataBean!!.forum!!.threadNum!!)
            if (mDataBean!!.forum!!.slogan.isNullOrEmpty()) {
                (headerSloganTextView.parent as View).visibility = View.GONE
            } else {
                (headerSloganTextView.parent as View).visibility = View.VISIBLE
                headerSloganTextView.text = mDataBean!!.forum!!.slogan
            }
            headerNameTextView.text = getString(R.string.text_forum_name, mDataBean!!.forum?.name)
            if ("1" == mDataBean!!.forum?.isLike) {
                if ("0" == mDataBean!!.forum?.signInInfo?.userInfo?.isSignIn) {
                    button.setText(R.string.button_sign_in)
                    button.isEnabled = true
                    toolbarEndBtn.setText(R.string.button_sign_in)
                    toolbarEndBtn.isEnabled = true
                } else {
                    button.setText(R.string.button_signed)
                    button.isEnabled = false
                    toolbarEndBtn.setText(R.string.button_signed)
                    toolbarEndBtn.isEnabled = false
                }
                tipTextView.text = getString(R.string.tip_forum_header_liked, mDataBean!!.forum?.userLevel, mDataBean!!.forum?.levelName)
            } else {
                button.setText(R.string.button_like)
                button.isEnabled = true
                toolbarEndBtn.setText(R.string.button_like)
                toolbarEndBtn.isEnabled = true
                tipTextView.text = mDataBean!!.forum?.slogan
            }
            /*
            when (mSortType) {
                ForumSortType.REPLY_TIME -> sortTypeText.setText(R.string.title_sort_by_reply)
                ForumSortType.SEND_TIME -> sortTypeText.setText(R.string.title_sort_by_send)
                ForumSortType.ONLY_FOLLOWED -> sortTypeText.setText(R.string.title_sort_by_like_user)
            }
            */
        } else {
            headerView.visibility = View.INVISIBLE
        }
    }

    private val currentFragment: Fragment
        get() = mAdapter!!.getItem(headerTabView.selectedTabPosition)

    private fun scrollToTop() {
        if (currentFragment is ScrollTopable) {
            (currentFragment as ScrollTopable).scrollToTop()
        }
    }

    private fun refreshForumInfo() {
        TiebaApi.getInstance().forumPage(forumName!!, 1).enqueue(object : Callback<ForumPageBean> {
            override fun onFailure(call: Call<ForumPageBean>, t: Throwable) {}

            override fun onResponse(call: Call<ForumPageBean>, response: Response<ForumPageBean>) {
                val forumPageBean = response.body()!!
                mDataBean!!.setForum(forumPageBean.forum)
                mDataBean!!.setAnti(forumPageBean.anti)
                refreshHeaderView()
            }

        })
    }

    override fun onBackPressed() {
        if (searchView.isOpen) {
            searchView.close()
        } else {
            if (Jzvd.backPress()) {
                return
            }
            super.onBackPressed()
        }
    }

    override fun onSuccess(forumPageBean: ForumPageBean) {
        this.mDataBean = forumPageBean
        forumName = forumPageBean.forum?.name
        refreshHeaderView()
        if (!animated) {
            AnimUtil.alphaOut(loadingView).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    loadingView.visibility = View.GONE
                }
            })
            animated = true
            if (fab.isOrWillBeHidden) {
                fab.show()
            }
        }
        if (firstLoaded) {
            firstLoaded = false
            historyHelper!!.writeHistory(History()
                    .setTitle(getString(R.string.title_forum, forumName))
                    .setTimestamp(System.currentTimeMillis())
                    .setAvatar(forumPageBean.forum?.avatar)
                    .setType(HistoryHelper.TYPE_BA)
                    .setData(forumName))
        }
    }

    override fun onFailure(errorCode: Int, errorMsg: String?) {
        refreshHeaderView()
    }

    companion object {
        private const val TAG = "ForumActivity"
        const val EXTRA_FORUM_NAME = "forum_name"

        @JvmStatic
        fun launch(
                context: Context,
                forumName: String
        ) {
            context.goToActivity<ForumActivity> {
                putExtra(EXTRA_FORUM_NAME, forumName)
            }
        }
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        if (tab.customView == null) tab.setCustomView(R.layout.layout_tab_arrow)
        val arrow = tab.customView!!.findViewById<ImageView>(R.id.arrow)
        AnimUtil.alphaIn(arrow, 150).withEndAction {
            arrow.visibility = View.VISIBLE
        }
    }

    override fun onTabUnselected(tab: TabLayout.Tab) {
        if (tab.customView == null) tab.setCustomView(R.layout.layout_tab_arrow)
        val arrow = tab.customView!!.findViewById<ImageView>(R.id.arrow)
        AnimUtil.alphaOut(arrow, 150).withEndAction {
            arrow.visibility = View.GONE
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab?) {
        val view = tab!!.view
        if (view.tag == null) {
            val arrow = tab.customView?.findViewById<ImageView>(R.id.arrow)
            val animSet = animSet {
                anim {
                    values = floatArrayOf(180f, 0f)
                    action = { value -> arrow?.rotation = value as Float }
                    duration = 150
                    interpolator = LinearInterpolator()
                }
                start()
            }
            val listPopupWindow = ListPopupWindow(this)
            PopupUtil.replaceBackground(listPopupWindow)
            listPopupWindow.anchorView = view
            listPopupWindow.width = ViewGroup.LayoutParams.WRAP_CONTENT
            listPopupWindow.height = ViewGroup.LayoutParams.WRAP_CONTENT
            val index = when (getSortType()) {
                ForumSortType.REPLY_TIME -> 0
                ForumSortType.SEND_TIME -> 1
                ForumSortType.ONLY_FOLLOWED -> 2
            }
            val adapter = SingleChooseAdapter(
                    this,
                    listOf(
                            getString(R.string.title_sort_by_reply),
                            getString(R.string.title_sort_by_send),
                            getString(R.string.title_sort_by_like_user)),
                    index
            )
            listPopupWindow.isModal = true
            listPopupWindow.setAdapter(adapter)
            listPopupWindow.setOnItemClickListener { _, _, position: Int, _ ->
                listPopupWindow.dismiss()
                setSortType(ForumSortType.valueOf(position))
            }
            listPopupWindow.setOnDismissListener {
                animSet.reverse()
                view.tag = null
            }
            listPopupWindow.show()
            view.tag = listPopupWindow
        }
    }
}