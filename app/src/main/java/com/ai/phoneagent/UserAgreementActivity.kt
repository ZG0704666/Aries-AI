package com.ai.phoneagent

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class UserAgreementActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_FLOW = "flow"
        private const val FLOW_ONBOARDING = "onboarding"
        private const val FLOW_VIEW_ONLY = "view_only"

        fun createOnboardingIntent(context: Context): Intent =
            Intent(context, UserAgreementActivity::class.java).putExtra(EXTRA_FLOW, FLOW_ONBOARDING)

        fun createViewIntent(context: Context): Intent =
            Intent(context, UserAgreementActivity::class.java).putExtra(EXTRA_FLOW, FLOW_VIEW_ONLY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_user_agreement)

        configureEdgeToEdge()

        val isOnboardingFlow = intent.getStringExtra(EXTRA_FLOW) != FLOW_VIEW_ONLY
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        val rootView = findViewById<LinearLayout>(R.id.cardAgreement)
        val headerView = findViewById<LinearLayout>(R.id.agreementHeader)
        val actionsView = findViewById<LinearLayout>(R.id.agreementActions)
        val contentView = findViewById<android.widget.TextView>(R.id.tvAgreementContent)
        val actionButton =
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAgreementAgree)

        val content = getString(R.string.user_agreement_content)
        contentView.text =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(content, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(content)
            }

        actionButton.text =
            getString(
                if (isOnboardingFlow) {
                    R.string.user_agreement_action_next
                } else {
                    R.string.action_close
                },
            )
        actionButton.setOnClickListener {
            if (isOnboardingFlow) {
                prefs.edit().putBoolean("user_agreement_accepted", true).apply()
                startActivity(PermissionGuideActivity.createIntent(this))
                overridePendingTransition(R.anim.m3t_slide_in_right, R.anim.m3t_slide_out_left)
                finish()
            } else {
                finishWithSlideBack()
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isOnboardingFlow) return
                    finishWithSlideBack()
                }
            },
        )

        applyWindowInsets(rootView, headerView, actionsView)
    }

    private fun finishWithSlideBack() {
        super.finish()
        overridePendingTransition(R.anim.m3t_slide_in_left, R.anim.m3t_slide_out_right)
    }

    private fun configureEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val pageColor = ContextCompat.getColor(this, R.color.m3t_drawer_background)
        val useLightSystemBarIcons = resources.getBoolean(R.bool.m3t_light_system_bars)
        window.statusBarColor = pageColor
        window.navigationBarColor = pageColor
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        WindowCompat.getInsetsController(window, window.decorView)?.let {
            it.isAppearanceLightStatusBars = useLightSystemBarIcons
            it.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }
    }

    private fun applyWindowInsets(root: View, header: View, actions: View) {
        val rootStart = root.paddingStart
        val rootEnd = root.paddingEnd
        val rootTop = root.paddingTop
        val rootBottom = root.paddingBottom
        val headerTop = header.paddingTop
        val actionsBottom = actions.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(
                rootStart + systemBars.left,
                rootTop,
                rootEnd + systemBars.right,
                rootBottom,
            )
            header.setPadding(
                header.paddingLeft,
                headerTop + systemBars.top,
                header.paddingRight,
                header.paddingBottom,
            )
            actions.setPadding(
                actions.paddingLeft,
                actions.paddingTop,
                actions.paddingRight,
                actionsBottom + systemBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}