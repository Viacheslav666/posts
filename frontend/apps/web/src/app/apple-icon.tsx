import { ImageResponse } from 'next/og';

export const size = { width: 180, height: 180 };
export const contentType = 'image/png';

export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#0B1F17',
          borderRadius: 36,
          color: '#3DDC97',
          fontSize: 96,
          fontWeight: 700,
          fontFamily: 'system-ui, sans-serif',
        }}
      >
        P
      </div>
    ),
    { ...size },
  );
}
