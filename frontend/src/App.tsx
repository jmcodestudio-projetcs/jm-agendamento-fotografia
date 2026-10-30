import { useState } from "react"

import AgendaPage from "@/pages/AgendaPage"
import ForgotPasswordPage from "@/pages/ForgotPasswordPage"
import LoginPage from "@/pages/LoginPage"
import ProfilePage from "@/pages/ProfilePage"
import ResetPasswordPage from "@/pages/ResetPasswordPage"
import ServicesPage from "@/pages/ServicesPage"
import SignupPage from "@/pages/SignupPage"

type Mode = "login" | "signup" | "forgot" | "reset" | "profile" | "services" | "agenda"

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
    case "profile":
      return (
        <ProfilePage
          onBack={() => setMode("login")}
          onOpenServices={() => setMode("services")}
          onOpenAgenda={() => setMode("agenda")}
        />
      )
    case "services":
      return <ServicesPage onBack={() => setMode("profile")} />
    case "agenda":
      return <AgendaPage onBack={() => setMode("profile")} />
    default:
      return (
        <LoginPage
          onSwitchToSignup={() => setMode("signup")}
          onForgotPassword={() => setMode("forgot")}
          onOpenProfile={() => setMode("profile")}
        />
      )
  }
}

export default App
