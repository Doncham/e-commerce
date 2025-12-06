package kr.hhplus.be.server.application.inventory;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {
	private final InventoryRepository inventoryRepository;
}
