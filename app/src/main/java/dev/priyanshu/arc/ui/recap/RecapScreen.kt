package dev.priyanshu.arc.ui.recap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.ui.theme.StreakSeasonTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun RecapRoute(
    arcId: String,
    onRunAgain: () -> Unit,
    onStartNew: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecapViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    // Re-load whenever the arc id changes; koinViewModel() caches by class, so
    // a fresh recap for a different arc still re-reads (same reason TodayRoute
    // drives refresh()).
    LaunchedEffect(arcId) { viewModel.load(arcId) }
    BackHandler(onBack = onBack)

    val state by viewModel.state.collectAsState()
    RecapScreen(state = state, onRunAgain = onRunAgain, onStartNew = onStartNew, modifier = modifier)
}

@Composable
fun RecapScreen(
    state: RecapUiState,
    onRunAgain: () -> Unit,
    onStartNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is RecapUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is RecapUiState.Unavailable ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recap for this arc.", textAlign = TextAlign.Center)
            }

        is RecapUiState.Loaded -> {
            // One card per screen, swiped vertically (docs/tasks/15). foundation's
            // VerticalPager — no new dependency.
            val pagerState = rememberPagerState(pageCount = { state.cards.size })
            VerticalPager(state = pagerState, modifier = modifier.fillMaxSize()) { page ->
                RecapCardView(
                    card = state.cards[page],
                    onRunAgain = onRunAgain,
                    onStartNew = onStartNew,
                )
            }
        }
    }
}

// Every card fills its slot and centres its content, taking size only from the
// slot it's given — never from screen measurements — so the next task can
// render these same composables into a fixed-size PNG (docs/tasks/15).
@Composable
private fun RecapCardView(
    card: RecapCard,
    onRunAgain: () -> Unit,
    onStartNew: () -> Unit,
) {
    // No per-page verticalScroll here: inside a VerticalPager it would swallow
    // the swipe and the pager would never advance. The cards are short; if one
    // ever needs to scroll, scroll an inner child, not the whole page.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (card) {
            is RecapCard.Title -> {
                Text(card.arcName, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(card.dateRange, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))
                Text(card.headline, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }

            is RecapCard.Intention -> {
                Label("Your intention")
                Spacer(Modifier.height(12.dp))
                Text(card.text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            }

            is RecapCard.BestStreak -> {
                Label("Best streak")
                Spacer(Modifier.height(12.dp))
                Big("${card.days}")
                Text(if (card.days == 1) "day" else "days", style = MaterialTheme.typography.titleMedium)
            }

            is RecapCard.HardestWeek -> {
                Label("Your toughest week")
                Spacer(Modifier.height(12.dp))
                Text(card.dateRange, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${card.completeDays} of ${card.countedDays} days",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            is RecapCard.WhatYouDid -> {
                Label("What you did")
                Spacer(Modifier.height(16.dp))
                card.rows.forEach { row ->
                    Text(row, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
            }

            is RecapCard.Milestones -> {
                Label("Milestones")
                Spacer(Modifier.height(16.dp))
                card.rows.forEach { row ->
                    Text(
                        "${row.label} — ${row.date}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    row.reflection?.let {
                        Text("“$it”", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            is RecapCard.Completion -> {
                Label("Completion")
                Spacer(Modifier.height(12.dp))
                Big("${card.percent}%")
            }

            RecapCard.WhatNext -> {
                Text("What next?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRunAgain, modifier = Modifier.fillMaxWidth()) { Text("Run it again") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onStartNew, modifier = Modifier.fillMaxWidth()) { Text("Start something new") }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun Big(text: String) {
    Text(text, style = MaterialTheme.typography.displayMedium)
}

@Preview(showBackground = true, name = "Recap — title")
@Composable
private fun RecapTitlePreview() {
    StreakSeasonTheme {
        RecapScreen(
            state = RecapUiState.Loaded(
                listOf(
                    RecapCard.Title("September Reset", "1 Sep 2026 – 10 Oct 2026", "You showed up 34 of 40 days."),
                ),
            ),
            onRunAgain = {},
            onStartNew = {},
        )
    }
}

@Preview(showBackground = true, name = "Recap — what next")
@Composable
private fun RecapWhatNextPreview() {
    StreakSeasonTheme {
        RecapScreen(
            state = RecapUiState.Loaded(listOf(RecapCard.WhatNext)),
            onRunAgain = {},
            onStartNew = {},
        )
    }
}
