package org.springframework.samples.petclinic;

import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.otel.bridge.Slf4JBaggageEventListener;
import io.micrometer.tracing.otel.bridge.Slf4JEventListener;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class MicrometerWithOtelComponent {

	@Value("${spring.application.name}")
	private String applicationName;

	@Value("${otlp.exporter.endpoint}")
	private String otlpEexporterEndpoint;

	@Bean
	public OpenTelemetry getOpenTelemetry() {
		Resource serviceNameResource = Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, applicationName));

		// [OTel component] SpanExporter is a component that gets called when a span is finished.
		OtlpGrpcSpanExporter otlpExporter =
			OtlpGrpcSpanExporter.builder()
				.setEndpoint(otlpEexporterEndpoint)
				.setTimeout(30, TimeUnit.SECONDS)
				.build();

		// [OTel component] SdkTracerProvider is an SDK implementation for TracerProvider
		SdkTracerProvider sdkTracerProvider =
			SdkTracerProvider.builder()
				.setResource(Resource.getDefault().merge(serviceNameResource))
				.addSpanProcessor(SimpleSpanProcessor.create(otlpExporter))
				.build();

		// [OTel component] The SDK implementation of OpenTelemetry
		OpenTelemetrySdk openTelemetrySdk =
			OpenTelemetrySdk.builder()
				.setTracerProvider(sdkTracerProvider)
				.setPropagators(ContextPropagators.create(B3Propagator.injectingSingleHeader()))
				.build();

		return openTelemetrySdk;
	}

	// taken from https://micrometer.io/docs/tracing#_supported_tracers
	@Bean
	public OtelTracer getOtelTracer(OpenTelemetry openTelemetry) {
		// [OTel component] Tracer is a component that handles the life-cycle of a span
		io.opentelemetry.api.trace.Tracer otelTracer = openTelemetry.getTracerProvider()
			.get("io.micrometer.micrometer-tracing");

		// [Micrometer Tracing component] A Micrometer Tracing wrapper for OTel
		OtelCurrentTraceContext otelCurrentTraceContext = new OtelCurrentTraceContext();

		// [Micrometer Tracing component] A Micrometer Tracing listener for setting up MDC
		Slf4JEventListener slf4JEventListener = new Slf4JEventListener();

		// [Micrometer Tracing component] A Micrometer Tracing listener for setting
		// Baggage in MDC. Customizable
		// with correlation fields (currently we're setting empty list)
		Slf4JBaggageEventListener slf4JBaggageEventListener = new Slf4JBaggageEventListener(Collections.emptyList());

		// [Micrometer Tracing component] A Micrometer Tracing wrapper for OTel's Tracer.
		// You can consider
		// customizing the baggage manager with correlation and remote fields (currently
		// we're setting empty lists)
		OtelTracer tracer = new OtelTracer(otelTracer, otelCurrentTraceContext, event -> {
			slf4JEventListener.onEvent(event);
			slf4JBaggageEventListener.onEvent(event);
		}, new OtelBaggageManager(otelCurrentTraceContext, Collections.emptyList(), Collections.emptyList()));

		return tracer;
	}
}
