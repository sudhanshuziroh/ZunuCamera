import kotlinx.coroutines.*

class TimerManager {

    private var job: Job? = null
    private var elapsedTime: Long = 0
    private var timerState: TimerState = TimerState.Stopped
    private var listener: TimerListener? = null

    fun startTimer() {
        if (timerState == TimerState.Running) {
            return
        }

        timerState = TimerState.Running
        job = CoroutineScope(Dispatchers.Main).launch {
            while (timerState == TimerState.Running) {
                delay(1000) // Delay for 1 second
                elapsedTime++
                listener?.onTimerTick(formatElapsedTime(elapsedTime))
            }
        }
    }

    fun stopTimer() {
        timerState = TimerState.Stopped
        job?.cancel()
        elapsedTime = 0
    }

    fun setTimerListener(listener: TimerListener) {
        this.listener = listener
    }

    private fun formatElapsedTime(timeInSeconds: Long): String {
        val hours = timeInSeconds / 3600
        val minutes = (timeInSeconds % 3600) / 60
        val seconds = timeInSeconds % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    interface TimerListener {
        fun onTimerTick(elapsedTime: String)
    }

    enum class TimerState {
        Running,
        Stopped
    }
}
