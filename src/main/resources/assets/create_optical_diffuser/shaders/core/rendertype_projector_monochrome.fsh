#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.1) {
        discard;
    }

    // Convert RGB to grayscale using standard Rec. 709 luminance weights
    float gray = dot(texColor.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Multiply luminance by vertexColor (vintage tint + lamp flicker) and ColorModulator
    vec3 monoColor = gray * vertexColor.rgb * ColorModulator.rgb;
    float alpha = texColor.a * vertexColor.a * ColorModulator.a;

    vec4 color = vec4(monoColor, alpha);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
