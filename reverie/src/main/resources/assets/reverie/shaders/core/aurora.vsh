#version 150

// Reverie aurora (client/dreamland/DreamSky), DefaultVertexFormat.POSITION_TEX_COLOR on the sky dome:
// UV0.x: distance along the ribbon (in "ribbon units", about one fold each); UV0.y: 0 at the lower edge, 1 at the top;
// Color: tint, alpha = intensity (already faded toward the ribbon's ends).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
