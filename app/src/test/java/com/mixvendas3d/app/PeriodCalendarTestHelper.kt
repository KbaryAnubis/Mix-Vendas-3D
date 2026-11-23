package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeUp
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.robolectric.Shadows

internal object PeriodCalendarTestHelper {
    private val previousMonthMatcher: SemanticsMatcher =
        hasContentDescription("previous", substring = true, ignoreCase = true) or
            hasContentDescription("anterior", substring = true, ignoreCase = true)

    private val nextMonthMatcher: SemanticsMatcher =
        hasContentDescription("next", substring = true, ignoreCase = true) or
            hasContentDescription("próximo", substring = true, ignoreCase = true)

    private fun dayCellMatcher(targetDate: LocalDate): SemanticsMatcher {
        val dayStr = targetDate.dayOfMonth.toString()
        val yearStr = targetDate.year.toString()
        val dayRegex = Regex("""(^|\D)$dayStr(\D|$)""")
        val yearRegex = Regex("""(^|\D)$yearStr(\D|$)""")
        val monthCandidates = listOf(
            Locale.getDefault(),
            Locale.US,
            Locale.forLanguageTag("pt-BR"),
        ).flatMap { locale ->
            listOf(
                targetDate.month.getDisplayName(TextStyle.FULL, locale),
                targetDate.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
            )
        }.map { it.lowercase(Locale.ROOT) }.distinct()

        return SemanticsMatcher("DatePicker day cell for $targetDate") { node ->
            if (SemanticsActions.OnClick !in node.config) return@SemanticsMatcher false
            val textCandidates = node.config.getOrNull(SemanticsProperties.Text)
                ?.map { it.text }
                .orEmpty()
            val descriptionCandidates = node.config.getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty()
            val allStrings = textCandidates + descriptionCandidates
            allStrings.any { candidate ->
                val lower = candidate.lowercase(Locale.ROOT)
                dayRegex.containsMatchIn(candidate) &&
                    yearRegex.containsMatchIn(candidate) &&
                    monthCandidates.any { month -> lower.contains(month) }
            }
        }
    }

    private fun monthHeaderMatcher(month: YearMonth): SemanticsMatcher {
        val yearStr = month.year.toString()
        val monthNames = listOf(
            Locale.getDefault(),
            Locale.US,
            Locale.forLanguageTag("pt-BR"),
        ).flatMap { locale ->
            listOf(
                month.month.getDisplayName(TextStyle.FULL, locale),
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
            )
        }.map { it.lowercase(Locale.ROOT) }.distinct()

        return SemanticsMatcher("Month header for $month") { node ->
            if (SemanticsActions.OnClick !in node.config) return@SemanticsMatcher false
            val strings = node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            strings.any { s ->
                val lower = s.lowercase(Locale.ROOT)
                s.contains(yearStr) && monthNames.any { m -> lower.contains(m) }
            }
        }
    }

    fun pickDate(
        composeRule: ComposeTestRule,
        fieldTag: String,
        targetDate: LocalDate,
        currentlySelectedDate: LocalDate? = null,
    ) {
        openPicker(composeRule, fieldTag)
        val seedMonth = YearMonth.from(currentlySelectedDate ?: LocalDate.now(ZoneId.systemDefault()))
        val targetMonth = YearMonth.from(targetDate)
        val diffMonths = ChronoUnit.MONTHS.between(seedMonth, targetMonth).toInt()
        var currentMonth = seedMonth
        when {
            diffMonths < 0 -> repeat(-diffMonths) {
                currentMonth = currentMonth.minusMonths(1)
                composeRule.onNode(previousMonthMatcher).performClick()
                val headerMatcher = monthHeaderMatcher(currentMonth)
                waitUntil(composeRule) {
                    composeRule.onAllNodes(headerMatcher).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()
            }
            diffMonths > 0 -> repeat(diffMonths) {
                currentMonth = currentMonth.plusMonths(1)
                composeRule.onNode(nextMonthMatcher).performClick()
                val headerMatcher = monthHeaderMatcher(currentMonth)
                waitUntil(composeRule) {
                    composeRule.onAllNodes(headerMatcher).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.waitForIdle()
            }
        }
        val targetDayMatcher = dayCellMatcher(targetDate)
        try {
            waitUntil(composeRule) {
                composeRule.onAllNodes(targetDayMatcher).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (t: Throwable) {
            val tree = runCatching {
                composeRule.onNodeWithTag("period_date_picker_dialog").printToString()
            }.getOrElse { it.toString() }
            throw AssertionError("Failed waiting for $targetDate (diffMonths=$diffMonths). Dialog tree:\n$tree", t)
        }
        composeRule.waitForIdle()
        val dayNode = composeRule.onAllNodes(targetDayMatcher).onFirst()
        scrollIfPossible(dayNode)
        composeRule.waitForIdle()
        fun isTargetDayVisibleInDialog(): Boolean {
            val dialogNode = composeRule.onAllNodesWithTag("period_date_picker_dialog")
                .fetchSemanticsNodes()
                .firstOrNull() ?: return false
            val node = composeRule.onAllNodes(targetDayMatcher)
                .fetchSemanticsNodes()
                .firstOrNull() ?: return false
            val dialogBounds = dialogNode.boundsInWindow
            val b = node.boundsInWindow
            return b.width > 0f && b.height >= 24f &&
                b.left >= dialogBounds.left - 16f && b.right <= dialogBounds.right + 16f
        }
        val verticalScrollerMatcher =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and
                hasAnyAncestor(hasTestTag("period_date_picker_dialog"))
        repeat(3) {
            if (isTargetDayVisibleInDialog()) return@repeat
            val scrollers = composeRule.onAllNodes(verticalScrollerMatcher).fetchSemanticsNodes()
            if (scrollers.isNotEmpty()) {
                composeRule.onAllNodes(verticalScrollerMatcher).onFirst().performTouchInput {
                    swipeUp(
                        startY = centerY + (bottom - top) * 0.25f,
                        endY = centerY - (bottom - top) * 0.25f,
                    )
                }
                composeRule.waitForIdle()
            }
        }
        waitUntil(composeRule) {
            isTargetDayVisibleInDialog()
        }
        dayNode.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_date_picker_confirm").performClick()
        waitUntil(composeRule) {
            composeRule.onAllNodesWithTag("period_date_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        val expectedBr = CurrencyFormatter.formatLocalDate(targetDate)
        scrollIfPossible(composeRule.onNodeWithTag(fieldTag))
            .assertTextContains(expectedBr, substring = true)
    }

    fun openPicker(
        composeRule: ComposeTestRule,
        fieldTag: String,
    ) {
        scrollIfPossible(composeRule.onNodeWithTag(fieldTag)).performClick()
        waitUntil(composeRule) {
            composeRule.onAllNodesWithTag("period_date_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollIfPossible(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        runCatching { node.performScrollTo() }
        return node
    }

    private fun waitUntil(
        composeRule: ComposeTestRule,
        timeoutMillis: Long = 5_000L,
        condition: () -> Boolean,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }
}
