package ru.yandex.practicum.contacts.presentation

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.yandex.practicum.contacts.data.models.*
import ru.yandex.practicum.contacts.domain.repository.ContactsRepository
import ru.yandex.practicum.contacts.domain.usecase.GetContactsUseCase
import ru.yandex.practicum.contacts.presentation.main.ContactsScreen
import ru.yandex.practicum.contacts.presentation.main.ContactsViewModel
import ru.yandex.practicum.contacts.presentation.ui.theme.ContactsTheme

/** Exercises the real screen with synthetic contacts; never reads device contacts. */
@OptIn(ExperimentalPermissionsApi::class)
@RunWith(AndroidJUnit4::class)
class BottomSheetRegressionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var model: ContactsViewModel

    @Test fun selectionsPersistAndFiltersComposeWithSorting() {
        val contacts = listOf(
            Contact(1, "Zoe", "Alpha", "+15550001", listOf(MessagingApp.TELEGRAM)),
            Contact(2, "Amy", "Zulu", "+445550002", listOf(MessagingApp.WHATS_APP)),
            Contact(3, "Bob", "Beta", "+495550003", listOf(MessagingApp.EMAIL)),
            Contact(4, "Carl", "Delta", "+15550004", listOf(MessagingApp.EMAIL))
        )
        val repository = object : ContactsRepository {
            override fun getContacts() = flowOf(contacts)
        }
        val permission = object : PermissionState {
            override val permission = "android.permission.READ_CONTACTS"
            override val status = PermissionStatus.Granted
            override fun launchPermissionRequest() = Unit
        }
        compose.setContent {
            model = remember { ContactsViewModel(GetContactsUseCase(repository)) }
            ContactsTheme { ContactsScreen(permissionState = permission, viewModel = model) }
        }
        compose.waitUntil { model.contacts.size == 4 }
        choose("Код страны", "+1")
        choose("Код страны", "+44")
        compose.runOnIdle {
            assertEquals(CountryCode.COMMON_CODES.take(2).toSet(), model.selectedCountryCodes)
        }
        compose.onNodeWithContentDescription("Код страны").performClick()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        compose.onAllNodes(isToggleable())[1].assertIsOn()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        choose("Фильтр", "TELEGRAM")
        choose("Фильтр", "WHATS_APP")
        compose.onNodeWithContentDescription("Фильтр").performClick()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        compose.onAllNodes(isToggleable())[1].assertIsOn()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.runOnIdle {
            assertEquals(setOf(MessagingApp.TELEGRAM, MessagingApp.WHATS_APP), model.selectedMessagingApps)
            assertEquals(listOf(2L, 1L), model.getFilteredAndSortedContacts().map { it.id })
        }
        compose.onNodeWithContentDescription("Сортировка").performClick()
        compose.onAllNodes(isToggleable())[2].performClick()
        compose.runOnIdle {
            assertEquals(listOf(1L, 2L), model.getFilteredAndSortedContacts().map { it.id })
            assertEquals(2, model.selectedCountryCodes.size)
            assertEquals(2, model.selectedMessagingApps.size)
        }
        choose("Код страны", "+1")
        choose("Фильтр", "TELEGRAM")
        compose.runOnIdle {
            assertEquals(setOf(CountryCode.COMMON_CODES[1]), model.selectedCountryCodes)
            assertEquals(setOf(MessagingApp.WHATS_APP), model.selectedMessagingApps)
            assertEquals(listOf(2L), model.getFilteredAndSortedContacts().map { it.id })
        }
        // Repeated changes must remain stable and must not duplicate selected values.
        repeat(3) { choose("Фильтр", "TELEGRAM"); choose("Фильтр", "TELEGRAM") }
        compose.runOnIdle { assertEquals(setOf(MessagingApp.WHATS_APP), model.selectedMessagingApps) }
        compose.onNodeWithContentDescription("Код страны").performClick()
        compose.onNodeWithText("Фильтр по коду страны").assertIsDisplayed()
        compose.onNodeWithText("Фильтр по коду страны").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 600f))
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Закрыть").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Фильтр").performClick()
        val roots = compose.onAllNodes(isRoot())
        roots[roots.fetchSemanticsNodes().lastIndex].performTouchInput { click(Offset(5f, 5f)) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Закрыть").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle { assertEquals(setOf(CountryCode.COMMON_CODES[1]), model.selectedCountryCodes) }
    }

    private fun choose(menu: String, text: String) {
        compose.onNodeWithContentDescription(menu).performClick()
        compose.onNodeWithText(text).performTouchInput { click() }
        compose.waitForIdle()
    }
}
