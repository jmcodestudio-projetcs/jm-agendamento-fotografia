import { useState } from "react"

import LoginPage from "@/pages/LoginPage"
import SignupPage from "@/pages/SignupPage"

type Mode = "login" | "signup"

function App() {
  const [mode, setMode] = useState<Mode>("login")

  return mode === "login" ? (
    <LoginPage onSwitchToSignup={() => setMode("signup")} />
  ) : (
    <SignupPage onSwitchToLogin={() => setMode("login")} />
  )
}

export default App
