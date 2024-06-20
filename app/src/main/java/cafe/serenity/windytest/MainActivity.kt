package cafe.serenity.windytest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.serenity.windytest.ui.theme.WindyTestTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile

// Everything done in a single file which normally is an anti-pattern
// but I strongly believe that toy problems requires toy solutions
class MainActivity : ComponentActivity() {

    private val viewModel: SummarizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.flow.collectAsState()
            SummarizerScreen(state, viewModel::onEvent)
        }
    }

    @Composable
    fun SummarizerScreen(state: SummarizerUIState, onEvent: (Event) -> Unit) {
        val focusManager = LocalFocusManager.current

        WindyTestTheme {
            // A surface container using the 'background' color from the theme
            Surface(
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "${state.processedVal}:\n${state.numbers.joinToString("\n")}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, true)
                            .verticalScroll(rememberScrollState())
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        TextField(
                            value = state.inputValue,
                            onValueChange = { onEvent(Event.UserInput(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = if (state.inputError.isBlank()) {
                                    ImeAction.Done
                                } else {
                                    ImeAction.None
                                }
                            ),
                            isError = state.inputError.isNotEmpty(),
                            singleLine = true,
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onEvent(Event.Summarize)
                                    focusManager.clearFocus()
                                }
                            ),
                            label = {
                                Text(
                                    state.inputError.ifBlank {
                                        "Enter a number"
                                    }
                                )
                            }
                        )
                        IconButton(
                            onClick = {
                                if(state.inputError.isBlank()) {
                                    onEvent(Event.Summarize)
                                    focusManager.clearFocus()
                                }
                            },
                            enabled = state.inputError.isBlank(),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Filled.Send, "Run")
                        }
                    }
                }
            }
        }
    }
}

class SummarizerViewModel : ViewModel() {
    private val _flow = MutableStateFlow(SummarizerUIState())
    private val userInputFlow = MutableStateFlow(UserInput())
    private val outputFlow = MutableStateFlow(Output())
    val flow: StateFlow<SummarizerUIState> = _flow.asStateFlow()

    init {
        // Here I could also combine it with the timer however it wont be useful
        // as trying to render with the same state won't do anything
        userInputFlow.combine(outputFlow) { userInput, output ->
            _flow.value = SummarizerUIState(
                inputValue = userInput.input,
                inputError = userInput.error,
                processedVal = output.outputFor,
                numbers = output.numbers
            )
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: Event) {
        when (event) {
            is Event.UserInput -> {
                val input = event.userInput
                var error = ""
                if (input.length > 2) {
                    error = "Input is to long"
                } else if (!input.all { it in '0'..'9' }) {
                    error = "Input is not numeric"
                }
                userInputFlow.value = UserInput(input, error)
            }

            is Event.Summarize -> {
                if (userInputFlow.value.error.isBlank()) {
                    val processedVal = userInputFlow.value.input.toInt()
                    if (outputFlow.value.outputFor != processedVal) {
                        outputFlow.value = Output(outputFor = processedVal)
                        (0 until processedVal).asFlow().onEach {
                            delay((it + 1) * 100L)
                        }.takeWhile {
                            outputFlow.value.outputFor == processedVal
                        }
                            // Probably you were looking for implementation of that sort however it's horribly inefficient
                            // And I would deeply prefer to just use map and calculate values for the array
//                        .map {
//                            it + 1
//                        }.runningFold(intArrayOf()) { array, element ->
//                            array + element
//                        }.map {
//                            it.runningReduce{ sum, element -> sum + element }.toIntArray()
//                        }
                            .map { element ->
                                IntArray(element + 1) {
                                    (it + 1) * (it + 2) / 2
                                }
                            }.onEach {
                                outputFlow.value = outputFlow.value.copy(numbers = it)
                            }.cancellable()
                            .launchIn(viewModelScope)
                    }
                }
            }
        }
    }
}

data class UserInput(val input: String = "", val error: String = "")

data class Output(val outputFor: Int = 0, val numbers: IntArray = intArrayOf()) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Output

        if (outputFor != other.outputFor) return false
        if (!numbers.contentEquals(other.numbers)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = outputFor
        result = 31 * result + numbers.contentHashCode()
        return result
    }

}

data class SummarizerUIState(
    val inputValue: String = "0",
    val inputError: String = "",
    val processedVal: Int = 0,
    val numbers: IntArray = intArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SummarizerUIState

        if (inputValue != other.inputValue) return false
        if (inputError != other.inputError) return false
        if (processedVal != other.processedVal) return false
        if (!numbers.contentEquals(other.numbers)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inputValue.hashCode()
        result = 31 * result + inputError.hashCode()
        result = 31 * result + processedVal
        result = 31 * result + numbers.contentHashCode()
        return result
    }
}

sealed class Event {
    class UserInput(val userInput: String) : Event()
    object Summarize : Event()
}