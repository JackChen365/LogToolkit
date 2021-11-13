## Read cached log file locally

> This document demonstrates how to read a cached log file and, in addition, filter the log by log level and keyword.

* We use the LogFileViewModel for us to initial the `LogfileAdapter`
```
val viewModel = ViewModelProvider(this, LogFileViewModelFactory(tempFile)).get(LogFileViewModel::class.java)
viewModel.getLogLiveData().observe(this) { pagedList ->
    val adapter = LogfileAdapter(this, tempFile)
    binding.recyclerView.adapter = adapter
    adapter.submitList(pagedList)
}
```

The `LogfileAdapter` is a PagedListAdapter which is means we use paging2 to load the log file.

* We use `LogFileViewModel` supports some functions to filter the log

```
//Filter the log by the given keyword.
binding.applyButton.setOnClickListener {
    viewModel.filterKeyword(binding.filterLogTag.text)
}
//Filter the log by the different log level.
binding.radioLayout.setOnCheckedChangeListener { _, index, isChecked ->
    if (isChecked) {
        viewModel.filterLevel(index + Log.VERBOSE)
    }
}
```


Something that is worth mentioning was we use the data source like a cursor in the database. 
We will never hold the text line inside the `PagedListAdapter`, instead we only store the position.

For instance:

```
line0: 0..120
line1: 120..240
line2: 360..480
```
We use a long variable to store the start positoin and end position.

```
var position:Long = (start<<32) + end

Resolve the start position and end position.
val start = position>>32
val end = position & 0xFFFFFFFF
```

That's it.