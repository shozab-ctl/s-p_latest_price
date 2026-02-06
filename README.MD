
## Overview

The Price Service is an in-memory service that allows clients to upload price data, track batch progress, fetch the latest prices, and cancel running batches. It uses atomic batch commits, asynchronous processing, and progress tracking for large datasets.

---

## Assumptions

### Bulk Upload Handling

> Note: The `upload` API is designed under the following assumption:
> 
 All data is expected to be sent in bulk.
 The service splits the input into chunks of 1000 records (`CHUNK_SIZE`) and processes each chunk asynchronously using virtual threads.
 Progress is tracked per batch, and clients can query batch status to monitor completion percentage.
 Asynchronous processing ensures that large datasets do not block the API, but may take time to fully commit all records.
 A future enhancement could include a separate API for batch uploads if incremental batches are needed.

## API Endpoints

| Endpoint                         | Method | Description                                                                                             |
| -------------------------------- | ------ | ------------------------------------------------------------------------------------------------------- |
| `/prices/upload`                 | POST   | Upload bulk price data asynchronously. Returns a batch UUID.                                            |
| `/prices/latest`                 | POST   | Fetch the latest prices for a given list of IDs. Returns `204 No Content` if data is not yet available. |
| `/prices/cancel/{batchId}`       | POST   | Cancel a running batch.                                                                                 |
| `/prices/batch/{batchId}/status` | GET    | Retrieve batch progress and status (IN_PROGRESS, COMPLETED, CANCELLED, FAILED).                         |

---

## Notes

1. `uploadAll` is asynchronous; the batch may remain in `IN_PROGRESS` for some time depending on dataset size.
2. The service commits prices atomically after processing each batch.
3. Clients should check batch status via `/batch/{batchId}/status` to ensure the upload has completed.
