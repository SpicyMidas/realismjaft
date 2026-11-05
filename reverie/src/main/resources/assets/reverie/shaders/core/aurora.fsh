#version 150

// Reverie aurora: slow curtains folding in an unseen wind. A bright, ragged lower hem; vertical rays that drift and
// flicker along the curtain; green at the hem turning teal, then violet and a little rose toward the top, where it
// thins out unevenly. Output is premultiplied and additive (blend ONE, ONE): it only ever adds light to the sky.

uniform vec4 ColorModulator;
uniform float GameTime;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.55;
    for (int i = 0; i < 4; i++) {
        sum += amp * noise(p);
        p = p * 2.03 + vec2(17.1, 5.3);
        amp *= 0.5;
    }
    return sum;
}

void main() {
    float t = GameTime * 1200.0; // seconds, wrapping once a day
    float u = texCoord0.x;
    float v = texCoord0.y;

    // the curtain folds: rays are bent sideways by a slow large-scale flow
    float fold = fbm(vec2(u * 0.35 + t * 0.012, t * 0.02)) - 0.5;
    float x = u + fold * 2.2 + v * fold * 0.8;

    // vertical rays: thin, stretched along v, drifting along the curtain
    float rays = noise(vec2(x * 9.0 + t * 0.09, v * 0.7 - t * 0.05));
    rays = 0.25 + 0.75 * pow(rays, 1.6);
    float fine = noise(vec2(x * 31.0 - t * 0.21, v * 1.5));
    rays *= 0.8 + 0.4 * fine;

    // the hem: bright, slightly ragged; the top thins out at an uneven height
    float hem = smoothstep(0.0, 0.05 + 0.04 * noise(vec2(x * 4.0, t * 0.3)), v);
    float topLine = 0.45 + 0.45 * fbm(vec2(x * 0.9 + 3.0, t * 0.015));
    float body = 1.0 - smoothstep(topLine * 0.6, topLine, v);
    float glowHem = exp(-v * 9.0) * 0.9;

    // waves of brightness running along the ribbon
    float pulse = 0.55 + 0.45 * sin(x * 1.7 - t * 0.35 + 2.0 * fbm(vec2(x * 0.5, t * 0.05)));

    vec3 green = vec3(0.28, 1.00, 0.58);
    vec3 teal = vec3(0.25, 0.85, 0.95);
    vec3 violet = vec3(0.62, 0.42, 1.00);
    vec3 rose = vec3(1.00, 0.48, 0.80);
    vec3 col = mix(green, teal, smoothstep(0.08, 0.35, v));
    col = mix(col, violet, smoothstep(0.35, 0.75, v));
    col = mix(col, rose, smoothstep(0.75, 1.0, v) * 0.6);
    col = mix(col, vec3(0.85, 1.0, 0.9), glowHem * 0.35);

    float a = vertexColor.a * hem * (body * rays + glowHem * rays) * pulse;
    if (a <= 0.002) {
        discard;
    }
    fragColor = vec4(col * vertexColor.rgb * a, a) * ColorModulator;
}
