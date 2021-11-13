## Real-time log

> For real-time log message. We have changed the how paging2 load the data from datasource.

* First, we use those worker state to indicate different fetching states.

```
companion object {
    private const val READY_TO_FETCH = 0
    private const val INITIAL_FETCHING = 1
    private const val APPEND_FETCHING = 2
    private const val PENDING_FETCHING = 3
    private const val DONE_FETCHING = 4
}
@IntDef(READY_TO_FETCH, INITIAL_FETCHING, APPEND_FETCHING, PENDING_FETCHING, DONE_FETCHING)
internal annotation class FetchState

@FetchState
private var mWorkerState = READY_TO_FETCH
``` 

You may be curious about the state `PENDING_FETCHING`.
That is: when we reach the end of the file. We are going to pending the load state and hold both `LoadRangeParams` and `LoadRangeCallback`

The class: `Logger` gives us the ability to observe the new message event.

```
fun observer(lifecycleOwner: LifecycleOwner, observer: Observer<Int>) {
    workThread.observer(lifecycleOwner, observer)
}

//Send the signal that we need to 
Logger.observer(this) { viewModel.invalidate() }
```


Once we make the the datasource invalid, We are actually will recreate the datasource instance.

```
override fun invalidate() {
    //When the worker state is pending. We need to fetch the data after user invoked invalidate.
    if (mWorkerState == PENDING_FETCHING) {
        mUpdateHandler.removeCallbacks(mRefreshAction)
        mUpdateHandler.postDelayed(mRefreshAction, UPDATE_THROTTLE)
    }
    //Only we change the filter keyword, and log level could make the worker state as DATA_IS_INVALID
    if (mWorkerState == DATA_IS_INVALID) {
        super.invalidate()
    }
}
```

So here we check the worker state to determine whether we need to load more data.

Once the worker state is `PENDING_FETCHING` means we reach the end of the file and wait for the update.
