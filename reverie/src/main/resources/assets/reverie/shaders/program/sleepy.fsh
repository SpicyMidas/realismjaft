#version 150

// Reverie Sleepyness (client/shader/ScreenEffects): heavy eyelids. The world dims and loses its colour toward the
// edges; the lids sink from above and below with soft, lashy edges; and in the dark at the edge of your vision the
// Shades of Dreamland sometimes stand for a moment, faint pale eyes and all, before you blink them away.
// Lid 0..1 (1 = closed), Dark 0..1, Glimpse 0..1 (how visible the Shades are), GlimpseX: where they stand (0..1).

uniform sampler2D DiffuseSampler;
uniform float Clock;
uniform float Lid;
uniform float Dark;
uniform float Glimpse;
uniform float GlimpseX;
uniform float Seed;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float hash(float n) {
    return fract(sin(n) * 43758.5453);
}

// signed distance to a thin standing figure: head, body, long arms (in screen units, y up)
float figure(vec2 p, float h) {
    vec2 head = p - vec2(0.0, 0.86 * h);
    float dHead = length(head * vec2(1.0, 0.95)) - 0.075 * h;
    vec2 b = p - vec2(0.0, 0.5 * h);
    float dBody = length(max(abs(b) - vec2(0.055 * h, 0.27 * h), 0.0)) - 0.03 * h;
    vec2 a = vec2(abs(p.x) - 0.09 * h, p.y - 0.47 * h);
    float dArm = length(max(abs(a) - vec2(0.012 * h, 0.25 * h), 0.0)) - 0.01 * h;
    vec2 l = vec2(abs(p.x) - 0.035 * h, p.y - 0.12 * h);
    float dLeg = length(max(abs(l) - vec2(0.022 * h, 0.13 * h), 0.0));
    return min(min(dHead, dBody), min(dArm, dLeg));
}

void main() {
    vec3 col = texture(DiffuseSampler, texCoord).rgb;
    vec2 c = texCoord - 0.5;
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 q = vec2(c.x * aspect, c.y);

    // darker, greyer surroundings
    float r = length(q);
    float vignette = smoothstep(0.25, 0.85, r);
    float grey = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(grey) * vec3(0.85, 0.85, 1.0), Dark * 0.6);
    col *= 1.0 - Dark * (0.25 + 0.65 * vignette);

    // Shades in the dark: two or three figures at the edge of sight, darker than the dark, with pinprick eyes
    if (Glimpse > 0.001) {
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float x = (GlimpseX - 0.5) * aspect + (fi - 1.0) * 0.16 + (hash(Seed + fi) - 0.5) * 0.08;
            float h = 0.28 + 0.12 * hash(Seed * 1.7 + fi);
            // standing on the horizon line, so they show in the slit between half-closed lids
            vec2 p = vec2(q.x - x, q.y + 0.14 - (hash(Seed + fi * 3.1) - 0.5) * 0.05);
            float d = figure(p, h);
            float shape = 1.0 - smoothstep(0.0, 0.012, d);
            float edgeSeen = 0.35 + 0.65 * smoothstep(0.05, 0.45, r); // clearest where the eye is already dim
            col = mix(col, vec3(0.0), shape * Glimpse * 0.85 * edgeSeen);
            vec2 e = p - vec2(0.0, 0.86 * h);
            float eyes = 1.0 - smoothstep(0.0, 0.006, min(length(e - vec2(-0.025 * h, 0.0)), length(e - vec2(0.025 * h, 0.0))) - 0.004);
            col += vec3(0.85, 0.82, 1.0) * eyes * Glimpse * edgeSeen * 0.9;
        }
    }

    // the eyelids: top and bottom, soft lashy edges that wobble a little
    // lids curve like real ones (lower at the middle of the top lid), with a soft, slightly uneven edge
    float xc = texCoord.x - 0.5;
    float curve = xc * xc * 0.35;
    float uneven = 0.006 * sin(texCoord.x * 23.0 + Clock * 0.5) + 0.004 * sin(texCoord.x * 61.0 + 1.3);
    float closeTop = 1.0 - Lid * 0.55 + curve * Lid;   // the top lid comes down further than the bottom one rises
    float closeBot = Lid * 0.47 - curve * Lid * 0.6;
    float top = smoothstep(closeTop - 0.10, closeTop + 0.02, texCoord.y + uneven);
    float bot = 1.0 - smoothstep(closeBot - 0.02, closeBot + 0.09, texCoord.y - uneven);
    float lids = max(top, bot);
    vec3 lidColor = vec3(0.045, 0.025, 0.05) + vec3(0.07, 0.02, 0.02) * (1.0 - lids); // a little blood-red at the edge
    col = mix(col, lidColor, lids);

    fragColor = vec4(col, 1.0);
}
