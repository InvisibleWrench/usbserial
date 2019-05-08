import 'dart:async';
import 'dart:typed_data';
import 'package:async/async.dart';
import 'types.dart';
import 'transformers.dart';

/// The transaction class is an easy way to
/// use the UsbPort class in a more linear way
/// without blocking.
///
/// Example
/// ```dart
/// // Create a parser that splits incoming data on endline newline combination ( \n\r)
/// var c = Transaction.terminated(p.inputStream, Uint8List.fromList([10, 13]));
///
/// // Listen asynchronously if you need this:
/// c.stream.listen((data) {
///   print("ASYNC LISTEN $data");
/// });
///
/// var request_1 = Uint8List.fromList([65, 10, 13]);
/// // Wait two seconds for the answer
///
/// Uint8List response_1 = await c.transaction(p, request_1, Duration(seconds: 2));
/// if (response_1 == null ) {
///    print("Failed to get a response.");
/// }
/// ```
///
class Transaction<T> {
  Stream<T> stream;
  StreamQueue<T> _queue;

  /// Create a transaction that transforms the incoming stream into
  /// events delimited by 'terminator'.
  static Transaction<Uint8List> terminated(
      Stream<Uint8List> stream, Uint8List terminator) {
    return Transaction<Uint8List>(stream
        .transform(TerminatedTransformer.broadcast(terminator: terminator)));
  }

  static Transaction<Uint8List> magicHeader(
      Stream<Uint8List> stream, List<int> header) {
    return Transaction<Uint8List>(stream.transform(
        MagicHeaderAndLengthByteTransformer.broadcast(header: header)));
  }

  static Transaction<String> stringTerminated(
      Stream<Uint8List> stream, Uint8List terminator) {
    return Transaction<String>(stream.transform(
        TerminatedStringTransformer.broadcast(terminator: terminator)));
  }

  /// Create a new transaction based stream without transforming the input.
  Transaction(Stream<T> stream) {
    this.stream = stream;
    _queue = StreamQueue<T>(stream);
  }

  /// Flush all existing messages from the queue.
  Future<void> flush() async {
    while (true) {
      // Don't call queue._next directly as it will
      // eat the next available message even if it
      // timed out. Use hasNext instead.
      var f = _queue.hasNext.timeout(Duration(microseconds: 1));
      try {
        bool hasNext = await f;
        if (!hasNext) {
          // The stream has closed, bail out!
          return;
        }
        await _queue.next;
        // consume the data and throw it away.
      } on TimeoutException {
        // Timeout occured, we are done, no more data
        // available.
        return;
      }
    }
  }

  /// Get the next message from the queue if any.
  /// returns data or null on error.
  Future<T> getMsg(Duration duration) async {
    // don't use the timeout on the .next property as
    // it will eat the next incoming packet.
    // instead use hasNext and then use
    try {
      bool b = await _queue.hasNext.timeout(duration);
      if (b) {
        return await _queue.next;
      } else {
        // throw TimeoutException("Port was closed.");
        return null;
      }
    } on TimeoutException {
      return null;
    }
  }

  /// The transaction functions does 3 things.
  /// 1. Flush the incoming queue
  /// 2. Write the message
  /// 3. Await the answer for at most "duration" time.
  /// returns List of bytes or null on timeout.
  Future<T> transaction(
      AsyncDataSinkSource port, Uint8List message, Duration duration) async {
    await flush();
    port.write(message);
    return getMsg(duration);
  }

  /// Call dispose when you are done with the object.
  /// this will release the underlying stream.
  void dispose() {
    _queue.cancel();
  }
}
