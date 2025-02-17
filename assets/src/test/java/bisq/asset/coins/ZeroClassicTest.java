/*
 * This file is part of Haveno.
 *
 * Haveno is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Haveno is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Haveno. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.asset.coins;

import bisq.asset.AbstractAssetTest;

import org.junit.Test;

public class ZeroClassicTest extends AbstractAssetTest {

    public ZeroClassicTest() {
        super(new ZeroClassic());
    }

    @Test
    public void testValidAddresses() {
        assertValidAddress("t1PLfc14vCYaRz6Nv1zxpKXhn5W5h9vUdUE");
        assertValidAddress("t1MjXvaqL5X2CquP8hLmvyxCiJqCBzuMofS");
    }

    @Test
    public void testInvalidAddresses() {
        assertInvalidAddress("17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem");
        assertInvalidAddress("38NwrYsD1HxQW5zfLT0QcUUXGMPvQgzTSn");
        assertInvalidAddress("8tP9rh3SH6n9cSLmV22vnSNNw56LKGpLrB");
        assertInvalidAddress("8Zbvjr");
    }
}
