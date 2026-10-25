import SwiftUI

struct LoginScreen: View {
    @EnvironmentObject private var model: AppModel
    @State private var login = UserDefaults.standard.string(forKey: "last-login") ?? ""   // only the login NAME is remembered
    @State private var password = ""
    @State private var showPassword = false
    @FocusState private var focus: Field?
    private enum Field { case login, password }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            LgIcon(name: "ic_chef_hat", color: Dt.orange, size: 72)
            Text("LokmaGo Restoran").font(.system(size: 26, weight: .heavy)).padding(.top, 10)
            Text("Restoran login va paroli bilan kiring").font(.system(size: 14)).foregroundStyle(Dt.textMuted).padding(.top, 2)

            VStack(spacing: 12) {
                field {
                    TextField("", text: $login, prompt: Text("Login").foregroundStyle(Dt.textMuted))
                        .textContentType(.username).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .submitLabel(.next).focused($focus, equals: .login).onSubmit { focus = .password }
                }
                field {
                    HStack {
                        Group {
                            if showPassword { TextField("", text: $password, prompt: Text("Parol").foregroundStyle(Dt.textMuted)) }
                            else { SecureField("", text: $password, prompt: Text("Parol").foregroundStyle(Dt.textMuted)) }
                        }
                        .textContentType(.password).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .submitLabel(.go).focused($focus, equals: .password).onSubmit(submit)
                        Button(showPassword ? "Yashirish" : "Ko'rsatish") { showPassword.toggle() }
                            .font(.system(size: 12)).foregroundStyle(Dt.textMuted)
                    }
                }
                if let error = model.error {
                    Text(error).font(.system(size: 13)).foregroundStyle(Dt.red).frame(maxWidth: .infinity, alignment: .leading)
                }
                PrimaryButton(title: "Kirish", loading: model.isLoading, enabled: !login.isEmpty && !password.isEmpty, action: submit).padding(.top, 8)
                Text("Bir marta kirsangiz, ilova sizni eslab qoladi.").font(.system(size: 12)).foregroundStyle(Dt.textMuted).padding(.top, 6)
            }
            .padding(.horizontal, 28).padding(.top, 28)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .lgScreen()
        .onChange(of: login) { _, _ in model.error = nil }
        .onChange(of: password) { _, _ in model.error = nil }
    }

    private func submit() {
        guard !model.isLoading, !login.isEmpty, !password.isEmpty else { return }
        focus = nil
        Task { await model.signIn(login: login, password: password) }
    }

    private func field<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        content().font(.system(size: 16)).foregroundStyle(Dt.text)
            .padding(.horizontal, 16).frame(height: 56)
            .background(Dt.card, in: RoundedRectangle(cornerRadius: Dt.radiusButton))
            .overlay(RoundedRectangle(cornerRadius: Dt.radiusButton).stroke(Dt.line, lineWidth: 1))
    }
}
