#version 150

// Reverie Dizziness (client/shader/ScreenEffects): the world will not hold still. The horizon bends into a slow
// smile, then a frown; everything breathes in a soft wave; the image turns a little around the centre and splits
// into a faint double with coloured fringes. Strength 0..~1.6, Bend -1..1 (where the horizon is bending now).

uniform sampler2D DiffuseSampler;
uniform float Clock;
uniform float Strength;
uniform float Bend;

in vec2 texCoord;
out vec4 fragColor;

vec3 sampleAt(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

void main() {
    float s = Strength;
    vec2 uv = texCoord;
    vec2 c = uv - 0.5;

    // the horizon bends: rows lift at the sides (or the middle), more toward the middle of the screen height
    float rowWeight = 1.0 - abs(c.y) * 1.4;
    uv.y += s * 0.075 * Bend * (c.x * c.x * 4.0 - 0.33) * rowWeight;

    // breathing waves
    uv.x += s * 0.010 * sin(uv.y * 8.0 + Clock * 1.9);
    uv.y += s * 0.008 * sin(uv.x * 6.0 - Clock * 1.4);

    // a slight turn around the centre, stronger near the middle
    float ang = s * 0.06 * sin(Clock * 0.55) * (1.0 - length(c));
    vec2 d = uv - 0.5;
    uv = 0.5 + vec2(d.x * cos(ang) - d.y * sin(ang), d.x * sin(ang) + d.y * cos(ang));

    // double vision with coloured fringes
    vec2 split = vec2(0.006, 0.002) * s * sin(Clock * 2.1);
    vec3 col;
    col.r = sampleAt(uv + split).r;
    col.g = sampleAt(uv).g;
    col.b = sampleAt(uv - split).b;
    vec3 ghost = sampleAt(uv + vec2(0.014, -0.008) * s * sin(Clock * 0.8 + 1.0));
    col = mix(col, ghost, 0.28 * min(s, 1.0));

    fragColor = vec4(col, 1.0);
}
