#version 150

// Reverie Sleepyness (client/shader/ScreenEffects): heavy eyelids. The world dims and loses its colour toward the
// edges, and the lids sink from above and below with soft, lashy edges. (The Shades you glimpse are real, client-only
// entities: client/shade/SleepyPhantoms.)
// Lid 0..1 (1 = closed), Dark 0..1.

uniform sampler2D DiffuseSampler;
uniform float Clock;
uniform float Lid;
uniform float Dark;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

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
