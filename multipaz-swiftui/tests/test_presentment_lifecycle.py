"""Run QR transaction lifecycle regressions with the existing macOS Swift compiler.

The task lifecycle is extracted verbatim from the production view model. Only the
SDK transaction body and UI/dependency declarations are replaced with local mocks.
This tests cancellation and error publication without devices or a SKIE runtime.
Run: python3 multipaz-swiftui/tests/test_presentment_lifecycle.py
"""
from pathlib import Path
import argparse
import hashlib
import subprocess
import tempfile

SOURCE_OVERRIDE = None


def run(output: Path):
    source = SOURCE_OVERRIDE or Path(__file__).resolve().parents[1] / "src/iosMain/swift/MdocProximityQrPresentment.swift"
    text = source.read_text()
    model = text[text.index("@MainActor\nfinal class MdocPresentmentViewModel"):text.index("\nprivate func onWaitingForRequest")]
    model = model.replace(": ObservableObject", "").replace("@Published ", "").replace("fileprivate ", "")
    model = model[:model.index("    // Dependencies")] + model[model.index("    func startTransaction"):]
    model = model.replace("func startTransaction(qrSettings: MdocProximityQrSettings)", "func startTransaction(session: MdocConnectionSession) -> Task<Void, Never>")
    body_start = model.index("            do {")
    body_end = model.index("            } catch {", body_start)
    model = model[:body_start] + '''            do {
                connectionSession = session
                self.state = .transacting
                if let error = session.primaryError { throw error }
                try await Task.sleep(nanoseconds: 3_600_000_000_000)
''' + model[body_end:]
    model = model.replace("\n    }\n    \n    func cancelTransaction()", "\n        return transactionTask!\n    }\n    \n    func cancelTransaction()", 1)
    prefix = '''import Foundation
enum PresentmentState { case prepareSettings, showQrCode, transacting, completed }
actor Gate {
    private var continuation: CheckedContinuation<Void, Error>?
    private(set) var entered = false
    func wait() async throws {
        entered = true
        try await withCheckedThrowingContinuation { continuation = $0 }
    }
    func release(_ error: Error?) {
        if let error { continuation?.resume(throwing: error) }
        else { continuation?.resume() }
        continuation = nil
    }
}
final class MdocConnectionSession: @unchecked Sendable {
    let gate = Gate()
    let primaryError: Error?
    var closeCalls = 0
    init(primaryError: Error? = nil) { self.primaryError = primaryError }
    func close() { closeCalls += 1 }
    func awaitClosed() async throws {
        precondition(!Task.isCancelled, "Cleanup must enter from an uncancelled task")
        try await gate.wait()
    }
}
'''
    tests = '''
@main struct LifecycleTests {
    @MainActor static func main() async {
        // A cancelled transaction must still finish cleanup without changing its replacement.
        let model = MdocPresentmentViewModel()
        let old = MdocConnectionSession()
        let oldTask = model.startTransaction(session: old)
        while model.state != .transacting { await Task.yield() }
        model.cancelTransaction()
        model.reset()
        while !(await old.gate.entered) { await Task.yield() }
        let current = MdocConnectionSession()
        let currentTask = model.startTransaction(session: current)
        while model.state != .transacting { await Task.yield() }
        await old.gate.release(NSError(domain: "old-cleanup", code: 1))
        await oldTask.value
        precondition(model.transactionError == nil, "Old cleanup error contaminated new transaction")
        precondition(model.state == .transacting, "Old transaction published completion")
        precondition(old.closeCalls > 0)
        model.cancelTransaction()
        while !(await current.gate.entered) { await Task.yield() }
        await current.gate.release(nil)
        await currentTask.value
        print("PASS: cancelled cleanup cannot publish into a replacement transaction")

        // A live replacement keeps its own primary and cleanup errors even if old cleanup fails.
        let stale = MdocConnectionSession()
        let staleTask = model.startTransaction(session: stale)
        while model.state != .transacting { await Task.yield() }
        model.cancelTransaction()
        while !(await stale.gate.entered) { await Task.yield() }
        let primary = NSError(domain: "current-primary", code: 2)
        let active = MdocConnectionSession(primaryError: primary)
        let activeTask = model.startTransaction(session: active)
        while !(await active.gate.entered) { await Task.yield() }
        await stale.gate.release(NSError(domain: "stale-cleanup", code: 3))
        await staleTask.value
        precondition(model.transactionError == nil, "Pending active error must remain task-local")
        let cleanup = NSError(domain: "current-cleanup", code: 4)
        await active.gate.release(cleanup)
        await activeTask.value
        let combined = model.transactionError! as NSError
        precondition((combined.userInfo[NSUnderlyingErrorKey] as? NSError) === primary)
        precondition(combined.localizedDescription.contains(cleanup.localizedDescription))
        precondition(model.state == .completed)
        print("PASS: current transaction retains its own primary and cleanup errors")
    }
}
'''
    output.mkdir(parents=True, exist_ok=True)
    probe = output / "PresentmentLifecycleTests.swift"
    probe.write_text(prefix + model + tests)
    print("PRODUCTION_SOURCE_SHA256=" + hashlib.sha256(source.read_bytes()).hexdigest(), flush=True)
    compiler = subprocess.check_output(["xcrun", "--find", "swiftc"], text=True).strip()
    subprocess.run([compiler, "-parse-as-library", "-swift-version", "5", "-module-cache-path", str(output / "module-cache"), str(probe), "-o", str(output / "tests")], check=True, timeout=60)
    subprocess.run([str(output / "tests")], check=True, timeout=30)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--source", type=Path, help="Optional archived production source for reproducing a regression")
    args = parser.parse_args()
    SOURCE_OVERRIDE = args.source.resolve() if args.source else None
    if args.output_dir:
        run(args.output_dir.resolve())
    else:
        with tempfile.TemporaryDirectory(prefix="multipaz-swift-lifecycle-") as directory:
            run(Path(directory))
