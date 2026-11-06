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

    vec3 rgb = texColor.rgb;

    // Fail-safe chroma glitch filter:
    // When WATERMeDIA's V-chroma (Cr) plane is uninitialized or dropped,
    // Red collapses to near zero while Green and Blue remain high and close to each other.
    // We mathematically detect this anomaly and reconstruct Red from Blue (Y luminance) while rebalancing Green.
    if (rgb.r < 0.08 && rgb.g > 0.10 && rgb.b > 0.06 && (rgb.g - rgb.r) > 0.10 && (rgb.b - rgb.r) > 0.05 && (rgb.g >= rgb.b - 0.12)) {
        rgb.r = rgb.b * 0.95;
        rgb.g = min(rgb.g, (rgb.b + rgb.r) * 0.54);
    }

    vec3 finalColor = rgb * vertexColor.rgb * ColorModulator.rgb;
    float alpha = texColor.a * vertexColor.a * ColorModulator.a;

    vec4 color = vec4(finalColor, alpha);
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
