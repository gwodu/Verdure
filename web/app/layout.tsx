import "./globals.css";
import type { ReactNode } from "react";
import { Nav } from "../components/Nav";

export const metadata = {
  title: "Verdure",
  description: "Web-first AI Chief of Staff"
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <main className="container">
          <Nav />
          {children}
        </main>
      </body>
    </html>
  );
}
