import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const protectedPaths = ["/today", "/settings", "/history", "/onboarding"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (!protectedPaths.some((path) => pathname.startsWith(path))) {
    return NextResponse.next();
  }

  const session = request.cookies.get("verdure_session")?.value;
  if (session) {
    return NextResponse.next();
  }

  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set("next", pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/today/:path*", "/settings/:path*", "/history/:path*", "/onboarding/:path*"]
};
