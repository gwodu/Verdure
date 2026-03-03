"use client";

import Link from "next/link";
import { useState } from "react";
import { logout } from "../lib/api";

const links = [
  { href: "/today", label: "Today" },
  { href: "/onboarding", label: "Onboarding" },
  { href: "/settings", label: "Settings" },
  { href: "/history", label: "History" },
  { href: "/login", label: "Login" }
];

export function Nav() {
  const [status, setStatus] = useState("");

  async function onLogout() {
    await logout();
    setStatus("Logged out");
    window.location.href = "/login";
  }

  return (
    <nav>
      <ul>
        {links.map((link) => (
          <li key={link.href}>
            <Link href={link.href}>{link.label}</Link>
          </li>
        ))}
        <li>
          <button className="secondary" onClick={() => void onLogout()}>
            Logout
          </button>
        </li>
      </ul>
      {status ? <p className="small">{status}</p> : null}
    </nav>
  );
}
