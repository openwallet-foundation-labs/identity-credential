import SwiftUI
import Multipaz

/**
 * Screen showing simulated credential provisioning and asking the user for approval
 * to send the verified captured portrait to the issuer.
 */
struct FakeIssuerApprovalScreen: View {
    @Environment(ViewModel.self) private var viewModel
    let capturedImages: [FacePose: ByteString]

    @State private var currentIndex: Int = 0

    private static let poseOrder: [FacePose] = [
        .front,
        .lookLeft,
        .lookRight,
        .lookUp,
        .lookDown
    ]

    private var orderedEntries: [(pose: FacePose, bytes: ByteString, image: UIImage?)] {
        Self.poseOrder.compactMap { pose in
            guard let bytes = capturedImages[pose] else { return nil }
            return (pose: pose, bytes: bytes, image: UIImage(data: bytes.toNSData()))
        }
    }

    var body: some View {
        let entries = orderedEntries
        ScrollView {
            VStack(spacing: 20) {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 28))
                        .foregroundColor(Color(red: 0.18, green: 0.49, blue: 0.20))
                    Text("Liveness Verified")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(Color(red: 0.18, green: 0.49, blue: 0.20))
                }
                .padding(.top, 8)

                if !entries.isEmpty {
                    ScrollViewReader { proxy in
                        VStack(spacing: 10) {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 16) {
                                    ForEach(Array(entries.enumerated()), id: \.offset) { index, entry in
                                        let isSelected = index == currentIndex
                                        VStack(spacing: 6) {
                                            if let uiImage = entry.image {
                                                Image(uiImage: uiImage)
                                                    .resizable()
                                                    .aspectRatio(contentMode: .fill)
                                                    .frame(width: 180, height: 230)
                                                    .clipShape(RoundedRectangle(cornerRadius: 20))
                                                    .overlay(
                                                        RoundedRectangle(cornerRadius: 20)
                                                            .stroke(
                                                                isSelected ? Color.accentColor : Color(.separator),
                                                                lineWidth: isSelected ? 3 : 1
                                                            )
                                                    )
                                            } else {
                                                RoundedRectangle(cornerRadius: 20)
                                                    .fill(Color(.secondarySystemBackground))
                                                    .frame(width: 180, height: 230)
                                                    .overlay(
                                                        Text("Decode Failed")
                                                            .font(.subheadline)
                                                            .foregroundColor(.secondary)
                                                    )
                                            }
                                            Text("\(entry.pose.displayLabel) (\(index + 1)/\(entries.count))")
                                                .font(.caption)
                                                .fontWeight(.semibold)
                                        }
                                        .id(index)
                                        .onTapGesture {
                                            withAnimation {
                                                currentIndex = index
                                                proxy.scrollTo(index, anchor: .center)
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 16)
                            }

                            if entries.count > 1 {
                                HStack(spacing: 16) {
                                    Button {
                                        let target = max(0, currentIndex - 1)
                                        withAnimation {
                                            currentIndex = target
                                            proxy.scrollTo(target, anchor: .center)
                                        }
                                    } label: {
                                        Image(systemName: "chevron.left.circle.fill")
                                            .font(.title2)
                                    }
                                    .disabled(currentIndex <= 0)

                                    Text("\(entries[currentIndex].pose.displayLabel) (\(currentIndex + 1) of \(entries.count))")
                                        .font(.subheadline)
                                        .fontWeight(.medium)

                                    Button {
                                        let target = min(entries.count - 1, currentIndex + 1)
                                        withAnimation {
                                            currentIndex = target
                                            proxy.scrollTo(target, anchor: .center)
                                        }
                                    } label: {
                                        Image(systemName: "chevron.right.circle.fill")
                                            .font(.title2)
                                    }
                                    .disabled(currentIndex >= entries.count - 1)
                                }
                            }
                        }
                    }
                } else {
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Color(.secondarySystemBackground))
                        .frame(width: 180, height: 230)
                        .overlay(
                            Text("No Portrait Available")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        )
                }

                VStack(alignment: .leading, spacing: 10) {
                    Text("Simulated Provisioning Request")
                        .font(.headline)
                        .fontWeight(.bold)

                    Divider()

                    issuerDetailRow(label: "Issuer", value: "Utopia Department of Motor Vehicles")
                    issuerDetailRow(label: "Credential", value: "Mobile Driving Licence (mDL)")
                    issuerDetailRow(
                        label: "Captured Poses",
                        value: entries.map { $0.pose.displayLabel }.joined(separator: ", ")
                    )
                    let totalKb = entries.reduce(0) { $0 + $1.bytes.toNSData().count } / 1024
                    issuerDetailRow(label: "Total Payload Size", value: "\(totalKb) KB (\(entries.count) frame(s))")
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))

                Text("Simulating provisioning. This is what a wallet app could send to the issuer after user approval.")
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
                    .padding(.horizontal, 8)

                VStack(spacing: 12) {
                    Button {
                        viewModel.pendingToastMessage = "Portrait approved and sent to issuer"
                        popBackToFaceMatcherPromptScreen()
                    } label: {
                        Text("Approve and Send to Issuer")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(entries.isEmpty)

                    Button("Cancel") {
                        viewModel.pendingToastMessage = "Provisioning cancelled"
                        popBackToFaceMatcherPromptScreen()
                    }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
                }
                .padding(.top, 8)
            }
            .padding(24)
        }
        .navigationTitle("Approve Portrait for Issuer")
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func issuerDetailRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .font(.subheadline)
                .fontWeight(.medium)
        }
    }

    private func popBackToFaceMatcherPromptScreen() {
        while viewModel.path.last != .faceMatcherPromptScreen && !viewModel.path.isEmpty {
            viewModel.path.removeLast()
        }
    }
}
