package co.edu.ufps.pedidos;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoRepository repositorio;
    private final RestClient restClient;
    private final String catalogoUrl;

    public PedidoController(
            PedidoRepository repositorio,
            @Value("${catalogo.url}") String catalogoUrl) {
        this.repositorio = repositorio;
        this.catalogoUrl = catalogoUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @GetMapping
    public List<Pedido> listar() {
        return repositorio.findAll();
    }

    @GetMapping("/{id}")
    public Pedido obtener(@PathVariable("id") Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pedido " + id + " no encontrado"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido crear(@Valid @RequestBody PedidoRequest request) {
        ProductoDTO producto = consultarProducto(request.getProductoId());

        if (producto.getStock() == null || producto.getStock() < request.getCantidad()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Stock insuficiente para el producto " + request.getProductoId()
                            + " (stock disponible: " + producto.getStock()
                            + ", solicitado: " + request.getCantidad() + ")");
        }

        Pedido pedido = new Pedido();
        pedido.setProductoId(producto.getId());
        pedido.setProductoNombre(producto.getNombre());
        pedido.setPrecioUnitario(producto.getPrecio());
        pedido.setCantidad(request.getCantidad());
        pedido.setTotal(producto.getPrecio() * request.getCantidad());
        pedido.setEstado("CONFIRMADO");
        pedido.setFechaCreacion(LocalDateTime.now());

        return repositorio.save(pedido);
    }

    private ProductoDTO consultarProducto(Long productoId) {
        ProductoDTO producto;
        try {
            producto = restClient.get()
                    .uri(catalogoUrl + "/productos/{id}", productoId)
                    .retrieve()
                    .body(ProductoDTO.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto " + productoId + " no encontrado en catalogo");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Servicio catalogo no disponible: " + ex.getMessage());
        }

        if (producto == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto " + productoId + " no encontrado en catalogo");
        }
        return producto;
    }
}
