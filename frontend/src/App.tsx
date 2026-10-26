import { useState } from "react"

import ForgotPasswordPage from "@/pages/ForgotPasswordPage"
import LoginPage from "@/pages/LoginPage"
import ResetPasswordPage from "@/pages/ResetPasswordPage"
import SignupPage from "@/pages/SignupPage"

type Mode = "login" | "signup" | "forgot" | "reset"

function readTokenFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("token")
}

function App() {
  const [token] = useState<string | null>(readTokenFromUrl)
  const [mode, setMode] = useState<Mode>(() => (token ? "reset" : "login"))

  function finishReset() {
    window.history.replaceState(null, "", window.location.pathname)
    setMode("login")
  }

  switch (mode) {
    case "signup":
      return <SignupPage onSwitchToLogin={() => setMode("login")} />
    case "forgot":
      return <ForgotPasswordPage onBackToLogin={() => setMode("login")} />
    case "reset":
      return <ResetPasswordPage token={token ?? ""} onDone={finishReset} />
    default:
      return (
        <LoginPage
          onSwitchToSignup={() => setMode("signup")}
          onForgotPassword={() => setMode("forgot")}
        />
      )
  }
}

export default App
